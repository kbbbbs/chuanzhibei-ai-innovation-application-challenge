package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.security.MessageDigest;

/** Private pixels and verifiable source associations; no model-generated geometry or public URLs. */
@Service
public class RagVisualService {
    public static final int MAX_REVISION_BYTES=32*1024*1024;
    private final JdbcTemplate db;
    private final RagAuthorizationService auth;
    private final ObjectMapper json;
    private final OcrLayoutParser parser=new OcrLayoutParser();
    private final VisualCropper cropper=new VisualCropper();
    public RagVisualService(JdbcTemplate db,RagAuthorizationService auth,ObjectMapper json){this.db=db;this.auth=auth;this.json=json;}

    /** Called inside the page checkpoint's fenced transaction. Existing checkpoints are idempotent. */
    public void capture(String doc,int version,int revision,int page,byte[] png,String raw,String text) {
        String fingerprint=sha(((raw==null?"":raw)+"\n--chosen--\n"+(text==null?"":text)+"\n--pixels--\n"+(png==null?"":sha(png))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var existing=db.queryForList("SELECT state_json FROM native_rag_visual_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",String.class,doc,version,revision,page);
        if(!existing.isEmpty()) {
            try {if(fingerprint.equals(json.readTree(existing.getFirst()).path("sourceFingerprint").asText()))return;}
            catch(Exception ignored){/* Rebuild corrupt derived metadata from its retained source. */}
            db.update("DELETE FROM native_rag_visual WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",doc,version,revision,page);
            db.update("DELETE FROM native_rag_visual_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",doc,version,revision,page);
        }
        var issues=new ArrayList<String>();OcrLayoutParser.Layout layout;
        try {layout=parser.parse(raw==null?"":raw);}
        catch(RagAccessException invalid){layout=new OcrLayoutParser.Layout(List.of(),List.of(),List.of("LAYOUT_INVALID"));}
        issues.addAll(layout.issues());
        long used=db.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM native_rag_visual WHERE document_id=? AND document_version=? AND parse_revision=?",Long.class,doc,version,revision);
        int stored=0;var storedVisuals=new ArrayList<RagVisual>();
        if(png!=null) {
            var regions=new ArrayList<VisualRegion>(layout.regions());regions.add(null);
            for(int i=0;i<regions.size();i++) {
                var region=regions.get(i);boolean fallback=region==null;
                if(fallback && stored>0)continue;
                if(fallback)issues.add("VISUAL_PAGE_FALLBACK");
                try {
                    var image=fallback?cropper.fullPage(png):cropper.crop(png,region.bbox());
                    if(used+image.png().length>MAX_REVISION_BYTES){issues.add("VISUAL_STORAGE_LIMIT");break;}
                    String context=fallback?bounded(text,900):bounded(region.context(),900);
                    String caption=fallback?"原页参考图，未定位插图":bounded(region.caption(),300);
                    String question=fallback?null:region.questionNumber();
                    String id=UUID.randomUUID().toString();
                    var metadata=new RagVisual(id,doc,version,revision,page,stored+1,fallback?"PAGE":"FIGURE",caption,context,
                            question,fallback?"PAGE_FALLBACK":"OCR_GROUNDING",true,"/api/rag/visuals/"+id+"/image",
                            image.width(),image.height(),fallback?List.of():region.bbox());
                    int[] anchor=fallback?null:anchor(text,context,caption,question);
                    db.update("INSERT INTO native_rag_visual(id,document_id,document_version,parse_revision,page_no,ordinal_number,metadata_json,anchor_start,anchor_end,image_png,image_sha256,byte_size) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            id,doc,version,revision,page,stored+1,write(metadata),anchor==null?null:anchor[0],anchor==null?null:anchor[1],image.png(),sha(image.png()),image.png().length);
                    stored++;used+=image.png().length;storedVisuals.add(metadata);
                } catch(RagAccessException invalid){issues.add("VISUAL_CROP_INVALID");}
            }
        }
        var state=new LinkedHashMap<String,Object>();state.put("version",1);state.put("issues",issues.stream().distinct().toList());
        state.put("sourceFingerprint",fingerprint);
        state.put("questions",questions(layout.blocks(),storedVisuals));
        db.update("INSERT INTO native_rag_visual_page(document_id,document_version,parse_revision,page_no,state_json) VALUES(?,?,?,?,?)",doc,version,revision,page,write(state));
    }

    public void issue(String doc,int version,int revision,int page,String code) {
        var state=db.queryForObject("SELECT state_json FROM native_rag_visual_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",String.class,doc,version,revision,page);
        try {
            var value=json.readTree(state);((com.fasterxml.jackson.databind.node.ArrayNode)value.path("issues")).add(code);
            db.update("UPDATE native_rag_visual_page SET state_json=? WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",value.toString(),doc,version,revision,page);
        } catch(Exception failure){throw new IllegalStateException("Visual state update failed");}
    }

    public Map<String,Object> pageView(String doc,int version,int revision,int page) {
        var state=db.queryForList("SELECT state_json FROM native_rag_visual_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",String.class,doc,version,revision,page);
        if(state.isEmpty())return Map.of("visualVersion",0,"visuals",List.of(),"visualIssues",List.of(),"questions",List.of());
        try {
            var metadata=json.readTree(state.getFirst());
            return Map.of("visualVersion",1,"visuals",list(doc,version,revision,page),"visualIssues",metadata.path("issues"),
                    "questions",json.convertValue(metadata.path("questions"),List.class));
        } catch(Exception failure){return Map.of("visualVersion",0,"visuals",List.of(),"visualIssues",List.of("VISUAL_METADATA_INVALID"));}
    }

    /** Actual chunk row supplies version/page/ranges. A serialized client hit cannot choose another source. */
    public List<RagVisual> forHit(RagSearchHit hit,int actorId) {
        var rows=db.queryForList("SELECT c.* FROM native_rag_chunk c JOIN native_rag_document d ON c.document_id=d.id WHERE c.id=? AND c.index_version=d.active_index_version",checked(hit.chunkId()));
        if(rows.isEmpty())throw new RagAccessException("404","图像来源片段不存在或版本已改变");
        var row=rows.getFirst();String doc=row.get("document_id").toString();authorize(doc,actorId);
        if(!doc.equals(hit.documentId()))throw new RagAccessException("403","图像来源不匹配");
        if(!(row.get("page_number") instanceof Number page))return List.of();
        int version=((Number)row.get("document_version")).intValue(),revision=((Number)row.get("parse_revision")).intValue();
        var visuals=db.queryForList("SELECT metadata_json,anchor_start,anchor_end FROM native_rag_visual WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=? ORDER BY ordinal_number",doc,version,revision,page.intValue());
        var matched=new ArrayList<RagVisual>();
        for(var visual:visuals) {
            if(row.get("char_start") instanceof Number start && row.get("char_end") instanceof Number end
                    && visual.get("anchor_start") instanceof Number a && visual.get("anchor_end") instanceof Number b
                    && (a.intValue()>=end.intValue() || b.intValue()<=start.intValue()))continue;
            matched.add(read(visual.get("metadata_json").toString()));
        }
        authorize(doc,actorId);return List.copyOf(matched);
    }

    public byte[] imageForActor(String id,int actorId) {
        var rows=db.queryForList("SELECT document_id,image_sha256,byte_size FROM native_rag_visual WHERE id=?",checked(id));
        if(rows.isEmpty())throw new RagAccessException("404","图像不存在");
        var row=rows.getFirst();String doc=row.get("document_id").toString();authorize(doc,actorId);
        byte[] data=db.queryForObject("SELECT image_png FROM native_rag_visual WHERE id=?",byte[].class,id);
        if(data==null || data.length!=((Number)row.get("byte_size")).intValue() || data.length>1024*1024 || !sha(data).equals(row.get("image_sha256")))
            throw new RagAccessException("409","图像校验失败，请重新解析来源");
        authorize(doc,actorId);return data;
    }
    private List<RagVisual> list(String doc,int version,int revision,int page){return db.queryForList("SELECT metadata_json FROM native_rag_visual WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=? ORDER BY ordinal_number",String.class,doc,version,revision,page).stream().map(this::read).toList();}
    private boolean exists(String doc,int v,int r,int p){return db.queryForObject("SELECT COUNT(*) FROM native_rag_visual_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_no=?",Integer.class,doc,v,r,p)>0;}
    private List<Map<String,Object>> questions(List<OcrLayoutParser.Block> blocks,List<RagVisual> visuals) {
        var groups=new LinkedHashMap<String,StringBuilder>();
        for(var block:blocks)if(block.questionNumber()!=null) {
            if(!groups.containsKey(block.questionNumber()) && groups.size()>=100)continue;
            var text=groups.computeIfAbsent(block.questionNumber(),key->new StringBuilder());
            if(text.length()+block.text().length()<6000)text.append(block.text()).append('\n');
        }
        var result=new ArrayList<Map<String,Object>>();
        groups.forEach((number,text)->result.add(Map.of("number",number,"text",text.toString().strip(),"needsReview",true,
                "visualIds",visuals.stream().filter(visual->number.equals(visual.questionNumber())).map(RagVisual::id).toList())));
        return List.copyOf(result);
    }
    private void authorize(String doc,int user){
        var actor=auth.actorForUser(user);var owner=db.queryForList("SELECT b.owner_id FROM native_rag_document d JOIN native_rag_base b ON d.kb_id=b.id WHERE d.id=?",Integer.class,doc);
        if(owner.isEmpty())throw new RagAccessException("404","资料已删除");auth.requireBaseOwner(actor,owner.getFirst());
    }
    private int[] anchor(String text,String context,String caption,String question){
        if(text==null)return null;
        // A diagram belongs to the whole recognized question, including later text chunks.
        // Shared page headings are context only and must never become the sole image anchor.
        if(question!=null && !question.isBlank()) {
            var pattern=java.util.regex.Pattern.compile("(?m)^[\\t ]*(?:第[\\t ]*([0-9]{1,3}|[一二三四五六七八九十百]{1,5})[\\t ]*题|[（(]([0-9]{1,3})[）)]|([0-9]{1,3})[.．、)）](?![0-9]))");
            var matches=pattern.matcher(text);var starts=new ArrayList<Integer>();var numbers=new ArrayList<String>();
            while(matches.find()) {
                starts.add(matches.start());
                for(int group=1;group<=3;group++)if(matches.group(group)!=null){numbers.add(matches.group(group));break;}
            }
            int selected=-1;
            for(int i=0;i<numbers.size();i++)if(question.equals(numbers.get(i))) {
                if(selected>=0){selected=-2;break;} // Repeated numbering is ambiguous; fall back to page association.
                selected=i;
            }
            if(selected>=0)return new int[]{starts.get(selected),selected+1<starts.size()?starts.get(selected+1):text.length()};
        }
        String value=caption==null?"":caption.strip();
        if(value.length()>=8) {
            int at=text.indexOf(value);
            if(at>=0 && text.indexOf(value,at+value.length())<0)return new int[]{at,at+value.length()};
        }
        // Without a reliable question range or unique source caption, retain page-level association.
        return null;
    }
    private String bounded(String value,int max){return com.cupk.aiacademy.rag.formula.FormulaText.safePrefix(value==null?"":value,max);}
    private RagVisual read(String data){try{return json.readValue(data,RagVisual.class);}catch(Exception e){throw new RagAccessException("409","图像元数据不可读取");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Visual metadata serialization failed");}}
    private static String checked(String value){try{return UUID.fromString(value).toString();}catch(Exception e){throw new RagAccessException("400","非法来源标识");}}
    private static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
