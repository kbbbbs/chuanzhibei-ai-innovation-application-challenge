package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.formula.FormulaText;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;

/** Interprets grounded OCR markup. Neighbor associations are source proximity, not verified image semantics. */
public final class OcrLayoutParser {
    public static final int MAX_REGIONS=6;
    private static final int MAX_BLOCKS=2000;
    private static final Pattern TAG=Pattern.compile("<\\|ref\\|>(.*?)<\\|/ref\\|>\\s*<\\|det\\|>(.*?)<\\|/det\\|>",Pattern.DOTALL);
    private static final Set<String> VISUAL=Set.of("image","figure","chart","diagram","picture");
    private static final Set<String> LABELS=Set.of("text","title","subtitle","sub_title","image","figure","chart","diagram","picture",
            "table","caption","figure_title","formula","equation","header","footer","page_header","page_footer","page_number","footnote","list","list_item");
    private static final Pattern QUESTION=Pattern.compile("^(?:第\\s*([0-9]{1,3}|[一二三四五六七八九十百]{1,5})\\s*题|(?:[（(]([0-9]{1,3})[）)]|([0-9]{1,3})[.．、)）])(?![0-9])\\s*)");
    private static final Pattern CAPTION=Pattern.compile("^(?:图\\s*[0-9一二三四五六七八九十]|(?i:figure|fig\\.)\\s*\\d).*",Pattern.DOTALL);
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public record Block(String type,String text,int sourceStart,int sourceEnd,String questionNumber) { }
    public record Layout(List<VisualRegion> regions,List<Block> blocks,List<String> issues) {
        public Layout { regions=List.copyOf(regions);blocks=List.copyOf(blocks);issues=List.copyOf(issues); }
    }
    private record Marker(String label,String coordinates,int start,int end) { }
    private record Pending(String label,List<Integer> bbox,int start,int end) { }

    public Layout parse(String raw) {
        if(raw==null || raw.isBlank())return new Layout(List.of(),List.of(),List.of());
        if(raw.length()>4*1024*1024)throw new RagAccessException("400","OCR布局文本超过处理上限");
        var issues=new LinkedHashSet<String>();var markers=new ArrayList<Marker>();
        var match=TAG.matcher(raw);
        while(match.find()) {
            if(markers.size()>=MAX_BLOCKS){issues.add("LAYOUT_BLOCK_LIMIT");break;}
            markers.add(new Marker(match.group(1).strip(),match.group(2),match.start(),match.end()));
        }
        var blocks=new BlockBuilder(issues);var candidates=new ArrayList<Pending>();var seen=new HashSet<List<Integer>>();
        int cursor=0;String currentType="text";
        for(var marker:markers) {
            blocks.append(raw.substring(cursor,marker.start()),cursor,currentType);
            String label=marker.label().toLowerCase(Locale.ROOT);
            currentType=LABELS.contains(label)?label:"text";
            if(VISUAL.contains(label)) {
                var coordinates=coordinates(marker.coordinates(),issues);
                for(var box:coordinates) {
                    if(!seen.add(box))continue;
                    if(candidates.size()>=MAX_REGIONS){issues.add("VISUAL_REGION_LIMIT");continue;}
                    candidates.add(new Pending(label,box,marker.start(),marker.end()));
                }
            } else if(!LABELS.contains(label)) {
                // Some provider responses place recognized words themselves inside ref.
                blocks.append(marker.label(),marker.start()+"<|ref|>".length(),"text");
            }
            cursor=marker.end();
        }
        blocks.append(raw.substring(cursor),cursor,currentType);
        var regions=new ArrayList<VisualRegion>();
        for(var candidate:candidates) {
            String question=null;
            for(var block:blocks.items)if(block.sourceEnd()<=candidate.start())question=block.questionNumber();
            var preceding=new ArrayList<Block>();var following=new ArrayList<Block>();
            for(var block:blocks.items) {
                if(block.sourceEnd()<=candidate.start()) {
                    boolean heading=Set.of("title","subtitle","sub_title","header","page_header").contains(block.type())
                            && !QUESTION.matcher(block.text()).find();
                    if(question==null || Objects.equals(question,block.questionNumber()) || heading)preceding.add(block);
                } else if(block.sourceStart()>=candidate.end()) {
                    if(!Objects.equals(question,block.questionNumber()))break;
                    boolean anotherImage=candidates.stream().anyMatch(p->p.start()>candidate.start() && p.start()<block.sourceStart());
                    if(anotherImage)break;
                    following.add(block);if(following.size()>=6)break;
                }
            }
            String caption="";
            for(var block:following)if(isCaption(block)){caption=block.text();break;}
            if(caption.isEmpty())for(int i=preceding.size()-1;i>=0;i--)if(isCaption(preceding.get(i))){caption=preceding.get(i).text();break;}
            var context=new StringBuilder();
            for(var block:preceding.subList(Math.max(0,preceding.size()-6),preceding.size()))appendContext(context,block.text());
            for(var block:following)appendContext(context,block.text());
            regions.add(new VisualRegion(regions.size()+1,candidate.label(),candidate.bbox(),candidate.start(),candidate.end(),
                    FormulaText.safePrefix(caption,500).strip(),FormulaText.safePrefix(context.toString(),2400).strip(),question));
        }
        return new Layout(regions,blocks.items,new ArrayList<>(issues));
    }

    private List<List<Integer>> coordinates(String encoded,Set<String> issues) {
        if(encoded.length()>4096){issues.add("VISUAL_COORDINATES_INVALID");return List.of();}
        try {
            var root=json.readTree(encoded);
            if(!root.isArray() || root.isEmpty() || root.size()>32)throw new IllegalArgumentException();
            var boxes=new ArrayList<List<Integer>>();
            for(var item:root) {
                if(!item.isArray() || item.size()!=4)throw new IllegalArgumentException();
                var box=new ArrayList<Integer>();
                for(var value:item) {
                    if(!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<0 || value.intValue()>999)
                        throw new IllegalArgumentException();
                    box.add(value.intValue());
                }
                if(box.get(0)>=box.get(2) || box.get(1)>=box.get(3))throw new IllegalArgumentException();
                boxes.add(List.copyOf(box));
            }
            return boxes;
        } catch(Exception invalid){issues.add("VISUAL_COORDINATES_INVALID");return List.of();}
    }

    private static boolean isCaption(Block block) {
        return Set.of("caption","figure_title").contains(block.type()) || CAPTION.matcher(block.text()).matches();
    }
    private static void appendContext(StringBuilder target,String text) {
        if(target.length()>6000)return;
        target.append(text).append('\n');
    }
    private static final class BlockBuilder {
        final List<Block> items=new ArrayList<>();final Set<String> issues;String question;
        BlockBuilder(Set<String> issues){this.issues=issues;}
        void append(String text,int offset,String type) {
            if(text.contains("<|ref|>") || text.contains("<|det|>"))issues.add("LAYOUT_MARKUP_INCOMPLETE");
            int at=0;
            for(String line:text.split("\n",-1)) {
                String value=line.strip();int start=at;
                while(start<at+line.length() && Character.isWhitespace(text.charAt(start)))start++;
                if(!value.isEmpty() && !value.contains("<|ref|>") && !value.contains("<|det|>")) {
                    if(items.size()>=MAX_BLOCKS){issues.add("LAYOUT_BLOCK_LIMIT");return;}
                    var found=QUESTION.matcher(value);
                    if(found.find())for(int i=1;i<=3;i++)if(found.group(i)!=null){question=found.group(i);break;}
                    items.add(new Block(type,value,offset+start,offset+start+value.length(),question));
                }
                at+=line.length()+1;
            }
        }
    }
}
