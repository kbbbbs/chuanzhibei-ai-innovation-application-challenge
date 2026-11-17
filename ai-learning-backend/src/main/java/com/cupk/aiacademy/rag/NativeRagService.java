package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.rag.ingestion.RagIngestionService;
import com.cupk.aiacademy.rag.graph.CourseKnowledgeGraphService;
import com.cupk.aiacademy.rag.graph.GraphRagEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

@Service
public class NativeRagService {
    private final JdbcTemplate jdbc;
    private final RagDocumentParser parser;
    private final RagEmbeddingClient embeddings;
    private final ObjectMapper mapper;
    private final RagAuthorizationService authorization;
    private final RagFileStore files;
    private final RagSchemaMigrator schema;
    private final RagIndexVersionRepository indexes;
    private final TransactionTemplate transaction;
    private static final int MAX_SEARCH_CHUNKS = 20000;
    private RagIngestionService ingestion;
    private RagRetrievalService retrieval;
    private CourseKnowledgeService courseKnowledge;
    private CourseKnowledgeGraphService courseGraphs;
    @Autowired void configureCourseKnowledge(CourseKnowledgeService service) { this.courseKnowledge = service; }
    @Autowired void configureCourseGraphs(CourseKnowledgeGraphService service) { this.courseGraphs = service; }
    @Autowired void configureRetrieval(RagRetrievalService retrieval){this.retrieval=retrieval;}
    private final com.cupk.aiacademy.rag.visual.RagVisualService visuals;

    /** Spring runtime always uses durable ingestion. Explicit legacy constructors keep isolated K01 fixtures usable. */
    @Autowired void configureIngestion(RagIngestionService ingestion){this.ingestion=ingestion;}

    public NativeRagService(JdbcTemplate jdbc, RagDocumentParser parser, RagEmbeddingClient embeddings,
                           ObjectMapper mapper, String storagePath) {
        this(jdbc,parser,embeddings,mapper,new RagAuthorizationService(null,jdbc),new RagFileStore(storagePath),
                new RagSchemaMigrator(jdbc),new RagIndexVersionRepository(jdbc));
    }

    @Autowired
    public NativeRagService(JdbcTemplate jdbc,RagDocumentParser parser,RagEmbeddingClient embeddings,
                            ObjectMapper mapper,RagAuthorizationService authorization,RagFileStore files,
                            RagSchemaMigrator schema,RagIndexVersionRepository indexes) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.embeddings = embeddings;
        this.mapper = mapper;
        this.authorization=authorization;this.files=files;this.schema=schema;this.indexes=indexes;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.visuals=new com.cupk.aiacademy.rag.visual.RagVisualService(jdbc,authorization,mapper);
        this.retrieval=new RagRetrievalService(embeddings,mapper);
    }

    @PostConstruct
    public void initialize() {
        initializeBaseTables();
        schema.migrate();
        files.initialize();
        retryFileCleanup();
    }

    /** Kept separate so the established MySQL vector-capacity contract has focused tests. */
    void initializeBaseTables() {
        // Additive initialization only. Never drops/replaces application or uploaded data.
        jdbc.execute("CREATE TABLE IF NOT EXISTS native_rag_base (id VARCHAR(36) PRIMARY KEY, owner_id INTEGER NOT NULL, name VARCHAR(200) NOT NULL, description TEXT NOT NULL, shared BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS native_rag_document (id VARCHAR(36) PRIMARY KEY, kb_id VARCHAR(36) NOT NULL, title VARCHAR(255) NOT NULL, file_name VARCHAR(255) NOT NULL, file_key VARCHAR(36) NOT NULL, sha256 VARCHAR(64) NOT NULL, mime_type VARCHAR(120) NOT NULL, file_size BIGINT NOT NULL, page_count INTEGER NOT NULL, chunk_count INTEGER NOT NULL, embedding_status VARCHAR(40) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT native_rag_document_hash UNIQUE(kb_id, sha256), CONSTRAINT native_rag_document_base_fk FOREIGN KEY(kb_id) REFERENCES native_rag_base(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS native_rag_chunk (id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL, kb_id VARCHAR(36) NOT NULL, page_number INTEGER, ordinal_number INTEGER NOT NULL, content TEXT NOT NULL, vector_json LONGTEXT, vector_model VARCHAR(64), CONSTRAINT native_rag_chunk_document_fk FOREIGN KEY(document_id) REFERENCES native_rag_document(id) ON DELETE CASCADE)");
        ensureVectorColumnCapacity();
    }

    private void ensureVectorColumnCapacity() {
        boolean widen = false;
        try (var connection = jdbc.getDataSource().getConnection()) {
            var metadata = connection.getMetaData();
            if (!"MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())) return;
            try (var columns = metadata.getColumns(connection.getCatalog(), null,
                    "native_rag_chunk", "vector_json")) {
                while (columns.next()) {
                    if ("native_rag_chunk".equals(columns.getString("TABLE_NAME")) &&
                            "vector_json".equals(columns.getString("COLUMN_NAME"))) {
                        widen = "TEXT".equalsIgnoreCase(columns.getString("TYPE_NAME"));
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Native RAG vector storage schema check failed");
        }
        // Release the metadata connection first so even a one-connection pool can execute the widening.
        if (widen) jdbc.execute("ALTER TABLE native_rag_chunk MODIFY COLUMN vector_json LONGTEXT NULL");
    }

    public Result createKnowledgeBase(String name, String description) {
        return result(() -> {
            Integer owner = owner();
            authorization.requirePersonalLibraryWrite(authorization.actorForUser(owner));
            String checkedName = validateName(name);
            String checkedDescription = validateDescription(description);
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO native_rag_base(id,owner_id,name,description,shared) VALUES(?,?,?,?,?)", id, owner, checkedName, checkedDescription, false);
            return baseView(base(id), owner);
        });
    }

    public Result listKnowledgeBases() { return listKnowledgeBases("all"); }

    public Result listKnowledgeBases(String scope) {
        return result(() -> {
            Integer user = owner();
            String filter = switch (scope == null ? "all" : scope) {
                case "all", "mine" -> "owner_id=?";
                case "shared" -> "owner_id=? AND 1=0";
                default -> throw invalid("scope 必须为 all、mine 或 shared");
            };
            if ("student".equals(authorization.actorForUser(user).roleCode())) return List.of();
            return jdbc.queryForList("SELECT * FROM native_rag_base WHERE " + filter + " ORDER BY created_at DESC,id", user)
                    .stream().map(b -> baseView(b, user)).toList();
        });
    }

    public Result getKnowledgeBase(String id) { return result(() -> baseView(readable(id, owner()), owner())); }

    public Result updateKnowledgeBase(String id, String name, String description, Boolean shared) {
        return result(() -> {
            Integer user = owner();
            return transaction.execute(status -> {
                Map<String,Object> b = writable(id, user);
                String n = name == null ? text(b, "name") : validateName(name);
                String d = description == null ? text(b, "description") : validateDescription(description);
                jdbc.update("UPDATE native_rag_base SET name=?,description=?,shared=? WHERE id=? AND owner_id=?",
                        n, d, shared == null ? bool(b.get("shared")) : shared, id, user);
                return baseView(base(id), user);
            });
        });
    }

    public Result deleteKnowledgeBase(String id) {
        return result(() -> {
            int user=owner();
            List<String> keys=transaction.execute(status->{
                writable(id,user);
                if (courseKnowledge != null && courseKnowledge.courseForBase(id) != null) throw invalid("课程专属资料库随课程归档，请在课程中管理资料");
                var objects=jdbc.queryForList("SELECT file_key FROM native_rag_document WHERE kb_id=? UNION SELECT v.file_key FROM native_rag_document_version v JOIN native_rag_document d ON d.id=v.document_id WHERE d.kb_id=?",String.class,id,id);
                objects.forEach(this::enqueueCleanup);
                jdbc.update("DELETE FROM native_rag_base WHERE id=? AND owner_id=?",id,user);
                return objects;
            });
            keys.forEach(this::cleanupFile);
            return Map.of("deleted",true);
        });
    }

    public Result uploadFile(String kbId, byte[] bytes, String filename) {
        if(ingestion!=null)return ingestion.submit(kbId,bytes,filename);
        return result(() -> ingest(kbId,null,bytes,filename));
    }

    /** Replace creates an immutable original-file revision; old explicit grants stay pinned to their version. */
    public Result replaceDocument(String documentId,byte[] bytes,String filename) {
        if(ingestion!=null)return ingestion.replace(documentId,bytes,filename);
        return result(() -> {
            Map<String,Object> current=document(documentId);
            writable(text(current,"kb_id"),owner());
            return ingest(text(current,"kb_id"),current,bytes,filename);
        });
    }

    private Map<String,Object> ingest(String kbId,Map<String,Object> previous,byte[] bytes,String filename) {
        int user=owner();writable(kbId,user);
        String fileName=safeFilename(filename);
        if(bytes==null || bytes.length==0 || bytes.length>RagDocumentParser.MAX_BYTES) throw invalid("文件不能为空且不能超过 20 MB");
        String hash=sha256(bytes);
        Map<String,Object> duplicate=duplicate(kbId,hash);
        if(duplicate!=null) {
            if(previous!=null && !text(previous,"id").equals(text(duplicate,"id")))
                throw new RagAccessException("409","该文件已属于本库的另一个文档");
            return withDuplicate(documentView(duplicate),true);
        }
        if(previous==null && jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=?",Integer.class,kbId)>=200)
            throw invalid("单个知识库最多存储 200 个文档");
        var parsed=parser.parse(bytes,fileName);
        List<double[]> vectors=List.of();String vectorStatus="not_configured";
        if(embeddings.isConfigured()) {
            try { vectors=embeddings.embed(parsed.chunks().stream().map(RagDocumentParser.ParsedChunk::content).toList());
                checkVectors(vectors,parsed.chunks().size());vectorStatus="indexed"; }
            catch(IllegalStateException failure){vectors=List.of();vectorStatus="provider_unavailable";}
        } else if(embeddings.isRequested()) vectorStatus="configuration_incomplete";
        String id=previous==null?UUID.randomUUID().toString():text(previous,"id");
        int version=previous==null?1:((Number)previous.get("document_version")).intValue()+1;
        String expected=previous==null?null:textOrNull(previous,"active_index_version");
        String key=UUID.randomUUID().toString();
        List<double[]> finalVectors=vectors;String finalStatus=vectorStatus;
        // Durable intent precedes publication of the original. Commit removes it; failed/abandoned writes can be cleaned.
        jdbc.update("INSERT INTO native_rag_file_cleanup(file_key,state) VALUES(?,'STAGING')",key);
        boolean stored=false;
        try {
            files.store(key,bytes);stored=true;
            transaction.executeWithoutResult(status->{
                writable(kbId,user);
                if(previous==null) {
                    jdbc.queryForList("SELECT id FROM native_rag_base WHERE id=? FOR UPDATE",kbId);
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=?",Integer.class,kbId)>=200)
                        throw invalid("单个知识库最多存储 200 个文档");
                    jdbc.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status,document_version,parse_revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1)",
                            id,kbId,fileName,fileName,key,hash,parsed.mimeType(),bytes.length,parsed.pageCount(),parsed.chunks().size(),finalStatus,version);
                } else {
                    var current=jdbc.queryForList("SELECT * FROM native_rag_document WHERE id=? FOR UPDATE",id);
                    if(current.isEmpty() || ((Number)current.getFirst().get("document_version")).intValue()!=version-1
                            || !java.util.Objects.equals(current.getFirst().get("active_index_version"),expected))
                        throw new RagAccessException("409","文档版本已改变，请刷新后重试");
                    jdbc.update("UPDATE native_rag_document SET title=?,file_name=?,file_key=?,sha256=?,mime_type=?,file_size=?,page_count=?,chunk_count=?,embedding_status=?,document_version=?,parse_revision=1 WHERE id=?",
                            fileName,fileName,key,hash,parsed.mimeType(),bytes.length,parsed.pageCount(),parsed.chunks().size(),finalStatus,version,id);
                }
                jdbc.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id) VALUES(?,?,?,?,?,?,?,?)",
                        id,version,key,hash,fileName,parsed.mimeType(),bytes.length,user);
                String index=indexes.createStaging(id,version,1,"native-chunker-v1",finalVectors.isEmpty()?null:embeddings.fingerprint(),
                        finalVectors.isEmpty()?null:embeddings.modelName(),finalVectors.isEmpty()?0:finalVectors.getFirst().length);
                for(int i=0;i<parsed.chunks().size();i++) {
                    var chunk=parsed.chunks().get(i);
                    insertChunk(id,kbId,chunk.page(),chunk.ordinal(),chunk.content(),finalVectors.isEmpty()?null:finalVectors.get(i),index,version,1);
                }
                indexes.activate(id,index,expected,finalVectors.isEmpty()?"KEYWORD_READY":"HYBRID_READY");
                jdbc.update("DELETE FROM native_rag_file_cleanup WHERE file_key=?",key);
            });
        } catch(Exception failure) {
            if(!stored && failure instanceof RagAccessException access && access.code().equals("409"))
                jdbc.update("DELETE FROM native_rag_file_cleanup WHERE file_key=?",key);
            else { enqueueCleanup(key);cleanupFile(key); }
            if(failure instanceof DuplicateKeyException && previous==null) {
                var existing=duplicate(kbId,hash);if(existing!=null)return withDuplicate(documentView(existing),true);
            }
            if(failure instanceof RuntimeException runtime)throw runtime;
            throw new IllegalStateException("文件存储失败");
        }
        return withDuplicate(documentView(document(id)),false);
    }

    private String insertChunk(String document,String base,Integer page,int ordinal,String content,double[] vector,
                             String index,int version,int parseRevision) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO native_rag_chunk(id,document_id,kb_id,page_number,ordinal_number,content,vector_json,vector_model,index_version,document_version,parse_revision,source_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,'DOCUMENT')",
                id,document,base,page,ordinal,content,vector==null?null:vectorJson(vector),
                vector==null?null:embeddings.fingerprint(),index,version,parseRevision);
        return id;
    }

    private void checkVectors(List<double[]> vectors,int expected) {
        if(vectors==null || vectors.size()!=expected || expected==0)throw new IllegalStateException("Incomplete vectors");
        int dimensions=vectors.getFirst().length;
        if(dimensions<1 || dimensions>4096)throw new IllegalStateException("Invalid vector dimensions");
        for(var vector:vectors) {
            if(vector==null || vector.length!=dimensions)throw new IllegalStateException("Inconsistent vectors");
            double norm=0;for(double value:vector){if(!Double.isFinite(value))throw new IllegalStateException("Invalid vector");norm+=value*value;}
            if(!Double.isFinite(norm) || norm<=0)throw new IllegalStateException("Invalid vector");
        }
    }

    public Result listKnowledge(String kbId, int limit, int offset) { return listKnowledgeForOwner(kbId, UserContext.getCurrentUserId(), limit, offset); }

    public Result listKnowledgeForOwner(String kbId, Integer ownerId, int limit, int offset) {
        return result(() -> {
            readable(kbId, requireOwner(ownerId));
            if (offset < 0) throw invalid("offset 不能为负数");
            int pageSize = Math.max(1, Math.min(200, limit));
            return jdbc.queryForList("SELECT * FROM native_rag_document WHERE kb_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?", kbId, pageSize, offset)
                    .stream().map(this::documentView).toList();
        });
    }

    public Result getDocument(String id) { return result(() -> documentView(readableDocument(id, owner()))); }

    public Result deleteDocument(String id) {
        return result(() -> {
            int user=owner();
            List<String> keys=transaction.execute(status->{
                var doc=document(id);writable(text(doc,"kb_id"),user);
                var objects=jdbc.queryForList("SELECT file_key FROM native_rag_document WHERE id=? UNION SELECT file_key FROM native_rag_document_version WHERE document_id=?",String.class,id,id);
                objects.forEach(this::enqueueCleanup);
                jdbc.update("DELETE FROM native_rag_document WHERE id=?",id);
                return objects;
            });
            keys.forEach(this::cleanupFile);
            return Map.of("deleted",true);
        });
    }

    private void enqueueCleanup(String key) {
        int updated=jdbc.update("UPDATE native_rag_file_cleanup SET state='PENDING',updated_at=CURRENT_TIMESTAMP WHERE file_key=?",key);
        if(updated==0)jdbc.update("INSERT INTO native_rag_file_cleanup(file_key,state) VALUES(?,'PENDING')",key);
    }

    /** Internal maintenance, not an HTTP operation. Old abandoned staging intents are recovered after a grace period. */
    int retryFileCleanup() {
        jdbc.update("UPDATE native_rag_file_cleanup SET state='PENDING' WHERE state='STAGING' AND created_at<?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)));
        int removed=0;
        for(String key:jdbc.queryForList("SELECT file_key FROM native_rag_file_cleanup WHERE state='PENDING' ORDER BY created_at LIMIT 1000",String.class))
            if(cleanupFile(key))removed++;
        return removed;
    }

    private boolean cleanupFile(String key) {
        // A legacy duplicate file key or an already committed writer must never lose its original.
        int references=jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE file_key=?",Integer.class,key)
                +jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE file_key=?",Integer.class,key);
        if(references>0){jdbc.update("DELETE FROM native_rag_file_cleanup WHERE file_key=?",key);return false;}
        try {
            files.delete(key);
            jdbc.update("UPDATE native_rag_file_cleanup SET state='COMPLETED',attempts=attempts+1,last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE file_key=?",key);
            return true;
        } catch(RagAccessException failure) {
            jdbc.update("UPDATE native_rag_file_cleanup SET state='PENDING',attempts=attempts+1,last_error='FILE_DELETE_FAILED',updated_at=CURRENT_TIMESTAMP WHERE file_key=?",key);
            return false;
        }
    }

    /** Rebuild into staging chunks, then atomically switch the active pointer without mutating the old index. */
    public Result reindexDocument(String id) {
        return result(() -> {
            int user=owner();var doc=document(id);writable(text(doc,"kb_id"),user);
            if(Set.of("QUEUED","PARSING","OCR","CHUNKING","EMBEDDING").contains(text(doc,"processing_state")))
                throw invalid("资料解析尚未结束，不能同时重建向量");
            if(!embeddings.isConfigured())throw invalid("请先配置并启用真实的向量模型服务");
            var chunks=rowsForDocument(id);List<double[]> vectors;
            try {vectors=embeddings.embed(chunks.stream().map(row->text(row,"content")).toList());checkVectors(vectors,chunks.size());}
            catch(IllegalStateException failure){throw new RagAccessException("503","向量服务不可用，原索引已保留");}
            String expected=textOrNull(doc,"active_index_version");
            transaction.executeWithoutResult(status->{
                var current=document(id);writable(text(current,"kb_id"),user);
                if(!java.util.Objects.equals(expected,current.get("active_index_version")))throw new RagAccessException("409","索引已改变，请刷新后重试");
                int version=((Number)doc.get("document_version")).intValue(),revision=((Number)doc.get("parse_revision")).intValue();
                var previousIndex=jdbc.queryForMap("SELECT chunk_revision,parser_revision FROM native_rag_index_version WHERE id=?",expected);
                String index=indexes.createStaging(id,version,revision,text(previousIndex,"chunk_revision"),embeddings.fingerprint(),embeddings.modelName(),vectors.getFirst().length,text(previousIndex,"parser_revision"));
                for(int i=0;i<chunks.size();i++) {
                    var row=chunks.get(i);Number page=(Number)row.get("page_number");
                    String chunk=insertChunk(id,text(doc,"kb_id"),page==null?null:page.intValue(),((Number)row.get("ordinal_number")).intValue(),text(row,"content"),vectors.get(i),index,version,revision);
                    jdbc.update("UPDATE native_rag_chunk SET char_start=?,char_end=?,source_type=? WHERE id=?",row.get("char_start"),row.get("char_end"),row.get("source_type"),chunk);
                }
                indexes.activate(id,index,expected,"HYBRID_READY");
                jdbc.update("UPDATE native_rag_document SET embedding_status='indexed' WHERE id=?",id);
            });
            return documentView(document(id));
        });
    }

    public record OriginalFile(Path path, String filename, String mimeType) { }

    public OriginalFile originalFile(String id) {
        Map<String,Object> doc=readableDocument(id,owner());
        var source=original(doc);
        readableDocument(id,owner());
        return source;
    }

    private OriginalFile original(Map<String,Object> source) {
        Path path=files.verified(text(source,"file_key"),((Number)source.get("file_size")).longValue(),text(source,"sha256"));
        return new OriginalFile(path,text(source,"file_name"),text(source,"mime_type"));
    }

    public Result grantDocument(String id,Integer courseId,int version,boolean readExcerpt,boolean downloadAllowed) {
        return result(() -> {
            int user=owner();var doc=document(id);writable(text(doc,"kb_id"),user);
            if (courseKnowledge != null) {
                Integer boundCourse = courseKnowledge.courseForBase(text(doc,"kb_id"));
                if (boundCourse != null && !boundCourse.equals(courseId)) throw new RagAccessException("403", "课程专属资料不能授权给其他课程");
            }
            var sourceVersion=version(id,version);
            if(readExcerpt && sourceVersion.get("active_index_version")==null)throw new RagAccessException("409","该版本尚未生成可读索引，请等待解析完成");
            requireCourseMaintenance(courseId,user);
            return transaction.execute(status->{
                writable(text(doc,"kb_id"),user);
                var existing=jdbc.queryForList("SELECT id FROM native_rag_course_grant WHERE course_id=? AND document_id=? AND document_version=?",courseId,id,version);
                String grant=existing.isEmpty()?UUID.randomUUID().toString():text(existing.getFirst(),"id");
                if(existing.isEmpty())jdbc.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt,download_allowed) VALUES(?,?,?,?,?,?,?)",grant,user,courseId,id,version,readExcerpt,downloadAllowed);
                else jdbc.update("UPDATE native_rag_course_grant SET owner_id=?,read_excerpt=?,download_allowed=?,revoked_at=NULL WHERE id=?",user,readExcerpt,downloadAllowed,grant);
                return Map.of("id",grant,"course_id",courseId,"document_id",id,"document_version",version,"read_excerpt",readExcerpt,"download_allowed",downloadAllowed);
            });
        });
    }

    public Result revokeDocumentGrant(String id,Integer courseId,int version) {
        return result(() -> {
            int user=owner();var doc=document(id);writable(text(doc,"kb_id"),user);
            if(courseId==null || courseId<=0 || version<=0)throw invalid("课程及版本无效");
            jdbc.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE document_id=? AND course_id=? AND document_version=? AND owner_id=?",id,courseId,version,user);
            return Map.of("revoked",true);
        });
    }

    private void requireCourseMaintenance(Integer courseId,int user) {
        if(courseId==null || courseId<=0)throw invalid("课程不能为空");
        var courses=jdbc.queryForList("SELECT teacher_id FROM t_course WHERE id=?",courseId);
        if(courses.isEmpty())throw new RagAccessException("404","课程不存在");
        if(!"admin".equals(authorization.actorForUser(user).roleCode()) &&
                !Integer.valueOf(user).equals(((Number)courses.getFirst().get("teacher_id")).intValue()))
            throw new RagAccessException("403","无权维护该课程的资料授权");
    }

    public Result courseContent(Integer courseId,String id,int documentVersion) {
        return result(() -> {
            var actor=authorization.currentActor();
            authorization.requireCourseDocumentRead(actor,courseId,id,documentVersion,false);
            var version=version(id,documentVersion);
            if(version.get("active_index_version")==null)throw new RagAccessException("409","资料尚未解析完成");
            var chunks=jdbc.queryForList("SELECT c.*,v.file_name AS title FROM native_rag_chunk c JOIN native_rag_document_version v ON v.document_id=c.document_id AND v.document_version=c.document_version WHERE c.document_id=? AND c.document_version=? AND c.index_version=v.active_index_version ORDER BY c.ordinal_number",id,documentVersion)
                    .stream().map(row->hit(row,0)).toList();
            Map<String,Object> view=new LinkedHashMap<>();
            for(String key:List.of("document_id","document_version","file_name","file_size","mime_type","active_index_version"))view.put(key,version.get(key));
            view.put("title",version.get("file_name"));view.put("chunks",chunks);
            view.put("content",String.join("\n\n",chunks.stream().map(RagSearchHit::content).toList()));
            authorization.requireCourseDocumentRead(actor,courseId,id,documentVersion,false);
            return view;
        });
    }

    public OriginalFile courseOriginalFile(Integer courseId,String id,int documentVersion) {
        var actor=authorization.currentActor();
        authorization.requireCourseDocumentRead(actor,courseId,id,documentVersion,true);
        var source=original(version(id,documentVersion));
        authorization.requireCourseDocumentRead(actor,courseId,id,documentVersion,true);
        return source;
    }

    private Map<String,Object> version(String id,int version) {
        if(version<1)throw invalid("文档版本无效");
        var rows=jdbc.queryForList("SELECT * FROM native_rag_document_version WHERE document_id=? AND document_version=?",checkedId(id),version);
        if(rows.isEmpty())throw new RagAccessException("404","文档版本不存在");
        return rows.getFirst();
    }

    public Result chunks(String documentId) {
        return result(() -> {
            readableDocument(documentId, owner());
            return rowsForDocument(documentId).stream().map(row -> withVisuals(hit(row, 0),owner())).toList();
        });
    }

    public Result chunk(String chunkId) {
        return result(() -> {
            List<Map<String,Object>> rows = jdbc.queryForList("SELECT c.*,d.title FROM native_rag_chunk c JOIN native_rag_document d ON c.document_id=d.id WHERE c.id=? AND c.index_version=d.active_index_version", checkedId(chunkId));
            if (rows.isEmpty()) throw new RagAccessException("404", "来源片段不存在");
            readable(text(rows.getFirst(), "kb_id"), owner());
            return withVisuals(hit(rows.getFirst(), 0),owner());
        });
    }

    public Result courseChunk(Integer courseId, String chunkId) {
        return result(() -> {
            RagActor actor = authorization.currentActor();
            authorization.requireCourseRead(actor, courseId);
            var rows = jdbc.queryForList("SELECT c.*,v.file_name AS title FROM native_rag_chunk c "
                    + "JOIN native_rag_document_version v ON v.document_id=c.document_id AND v.document_version=c.document_version "
                    + "WHERE c.id=? AND c.index_version=v.active_index_version", checkedId(chunkId));
            if (rows.isEmpty()) throw new RagAccessException("404", "来源片段不存在或索引已更新，请重新提问或检索");
            var row = rows.getFirst();
            String documentId = text(row, "document_id");
            int version = ((Number)row.get("document_version")).intValue();
            authorization.requireCourseDocumentRead(actor, courseId, documentId, version, false);
            RagSearchHit source = withCourseVisuals(hit(row, 0), actor.userId(), courseId);
            authorization.requireCourseDocumentRead(actor, courseId, documentId, version, false);
            return source;
        });
    }

    public Result contentForOwner(String documentId, Integer ownerId) {
        return result(() -> {
            Map<String,Object> doc = readableDocument(documentId, requireOwner(ownerId));
            if(doc.get("active_index_version")==null)throw new RagAccessException("409","资料正在处理或解析失败，请检查任务状态后重试");
            Map<String,Object> data = new LinkedHashMap<>(documentView(doc));
            List<RagSearchHit> chunks = rowsForDocument(documentId).stream().map(row -> hit(row, 0)).toList();
            data.put("chunks", chunks);
            data.put("content", String.join("\n\n", chunks.stream().map(RagSearchHit::content).toList()));
            return data;
        });
    }

    public List<String> requireReadableBaseIds(List<String> ids) { return requireReadableBaseIdsForOwner(ids, owner()); }

    public List<String> requireReadableBaseIdsForOwner(List<String> ids, Integer ownerId) {
        requireOwner(ownerId);
        if (ids == null || ids.isEmpty()) throw invalid("请选择至少一个知识库");
        List<String> unique = ids.stream().distinct().toList();
        if (unique.size() > 20) throw invalid("一次最多检索 20 个知识库");
        for (String id : unique) readable(id, ownerId);
        return unique;
    }

    private static final String SEARCH_SELECT="SELECT c.*,d.title,iv.embedding_fingerprint AS index_embedding_fingerprint,iv.embedding_model AS index_embedding_model,iv.dimensions AS index_dimensions,iv.normalized AS index_normalized,iv.chunk_revision AS index_chunk_revision,iv.state AS index_state,iv.parser_revision AS index_parser_revision,iv.document_version AS index_document_version,iv.parse_revision AS index_parse_revision FROM native_rag_chunk c JOIN native_rag_document d ON c.document_id=d.id JOIN native_rag_index_version iv ON iv.id=d.active_index_version AND iv.document_id=d.id";
    private static final String SEARCH_READY="c.index_version=d.active_index_version AND c.document_version=iv.document_version AND c.parse_revision=iv.parse_revision AND iv.state IN ('KEYWORD_READY','HYBRID_READY')";
    private static final String COURSE_GRANT_JOIN=" JOIN native_rag_course_grant g ON g.document_id=d.id AND g.document_version=c.document_version AND g.course_id=? AND g.revoked_at IS NULL AND g.read_excerpt=TRUE";

    public Result search(RagSearchRequest request) {
        if (request == null) return Result.error("400", "检索参数不能为空");
        if (request.courseId() != null) return searchForCourse(request.courseId(),request.query(),UserContext.getCurrentUserId(),request.limit() == null ? 6 : request.limit());
        return searchForOwner(request.kbIds(), request.query(), UserContext.getCurrentUserId(), request.limit() == null ? 6 : request.limit());
    }

    public Result searchForOwner(List<String> kbIds, String query, Integer ownerId, int limit) {
        return result(() -> {
            List<String> ids = requireReadableBaseIdsForOwner(kbIds, ownerId);
            if (query == null || query.isBlank() || query.length() > 2000) throw invalid("检索问题不能为空且不能超过 2000 字");
            var before=activeSnapshot(ids);
            String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
            List<Map<String,Object>> rows=jdbc.queryForList(SEARCH_SELECT+" WHERE "+SEARCH_READY+" AND c.kb_id IN ("+placeholders+") ORDER BY c.id LIMIT "+(MAX_SEARCH_CHUNKS+1),ids.toArray());
            return rank(query,limit,rows,()->{
                requireReadableBaseIdsForOwner(ids,ownerId);
                if(!before.equals(activeSnapshot(ids)))throw new RagAccessException("409","资料索引已改变，请重新检索");
            },ownerId);
        });
    }

    public void requireCourseRead(Integer courseId) {
        if (courseKnowledge != null && courseKnowledge.isCourseManager(courseId, UserContext.getCurrentUserId())) return;
        authorization.requireCourseRead(authorization.currentActor(), courseId);
    }

    /** Course members search only documents granted to that course, at the granted version, without seeing the owner's library. */
    public Result searchForCourse(Integer courseId, String query, Integer userId, int limit) {
        if (courseKnowledge != null && courseKnowledge.isCourseManager(courseId, userId))
            return searchOwnedCourse(courseId,query,userId,limit);
        return result(() -> {
            Runnable allowed=()->authorization.requireCourseRead(authorization.actorForUser(userId),courseId);
            allowed.run();
            if (query == null || query.isBlank() || query.length() > 2000) throw invalid("检索问题不能为空且不能超过 2000 字");
            var before=courseSnapshot(courseId);
            List<Map<String,Object>> rows=jdbc.queryForList(SEARCH_SELECT+COURSE_GRANT_JOIN+" WHERE "+SEARCH_READY+" ORDER BY c.id LIMIT "+(MAX_SEARCH_CHUNKS+1),courseId);
            Runnable stillAuthorized=()->{
                allowed.run();
                if(!before.equals(courseSnapshot(courseId)))throw new RagAccessException("409","资料索引已改变，请重新检索");
            };
            var baseline=rank(query,limit,rows,stillAuthorized,null);
            var enriched = enrichCourse(courseId,query,limit,rows,baseline,stillAuthorized,null);
            var withImages = enriched.hits().stream().map(source -> withCourseVisuals(source,userId,courseId)).toList();
            var paths = enriched.graphPaths().stream().map(path -> path.retainCitations(withImages))
                    .filter(java.util.Objects::nonNull).toList();
            stillAuthorized.run();
            return new RagSearchResponse(withImages,enriched.mode(),enriched.warning(),enriched.actualMode(),
                    enriched.degradedReason(),enriched.indexVersions(),paths);
        });
    }

    private Result searchOwnedCourse(Integer courseId,String query,Integer userId,int limit) {
        return result(() -> {
            List<String> ids=List.of(courseKnowledge.baseId(courseId));
            requireReadableBaseIdsForOwner(ids,userId);
            if(query==null || query.isBlank() || query.length()>2000)
                throw invalid("检索问题不能为空且不能超过 2000 字");
            var before=activeSnapshot(ids);
            Runnable stillAuthorized=()->{
                var actor = authorization.actorForUser(userId);
                if (!"teacher".equals(actor.roleCode()) || !courseKnowledge.isCourseManager(courseId,userId))
                    throw new RagAccessException("403", "无权管理该课程资料");
                requireReadableBaseIdsForOwner(ids,userId);
                if(!before.equals(activeSnapshot(ids)))throw new RagAccessException("409","资料索引已改变，请重新检索");
            };
            List<Map<String,Object>> rows=jdbc.queryForList(SEARCH_SELECT+" WHERE "+SEARCH_READY
                    +" AND c.kb_id=? ORDER BY c.id LIMIT "+(MAX_SEARCH_CHUNKS+1),ids.getFirst());
            var baseline=rank(query,limit,rows,stillAuthorized,userId);
            return enrichCourse(courseId,query,limit,rows,baseline,stillAuthorized,userId);
        });
    }

    private RagSearchHit withCourseVisuals(RagSearchHit hit, int actor, int course) {
        return new RagSearchHit(hit.chunkId(),hit.documentId(),hit.kbId(),hit.title(),hit.content(),hit.page(),
                hit.ordinal(),hit.score(),hit.citation(),visuals.forHit(hit,actor,course),
                hit.indexVersion(),hit.documentVersion(),hit.parseRevision());
    }

    private RagSearchResponse enrichCourse(Integer courseId,String query,int limit,List<Map<String,Object>> rows,
            RagSearchResponse baseline,Runnable stillAuthorized,Integer visualActor) {
        if(courseGraphs==null)return baseline;
        Map<String, Object> graph;
        try {
            graph = courseGraphs.readForRetrieval(courseId);
        } catch (RagAccessException unavailable) {
            if (!"503".equals(unavailable.code())) throw unavailable;
            stillAuthorized.run();
            String warning = (baseline.warning() == null ? "" : baseline.warning() + "；")
                    + "Neo4j 图谱暂不可用，本次使用已授权的课程资料检索。";
            return new RagSearchResponse(baseline.hits(), baseline.mode(), warning,
                    baseline.actualMode(), baseline.degradedReason(), baseline.indexVersions(), List.of());
        }
        var candidates=rows.stream().map(row->hit(row,0)).toList();
        var enriched=GraphRagEnricher.enrich(courseId,query,graph,baseline,candidates,limit);
        if(enriched!=baseline && visualActor!=null) {
            var withImages=enriched.hits().stream().map(source->withVisuals(source,visualActor)).toList();
            var paths=enriched.graphPaths().stream().map(path->path.retainCitations(withImages))
                    .filter(java.util.Objects::nonNull).toList();
            enriched=new RagSearchResponse(withImages,enriched.mode(),enriched.warning(),enriched.actualMode(),
                    enriched.degradedReason(),enriched.indexVersions(),paths);
        }
        // Graph loading and image hydration may perform I/O after the baseline ranker's last check.
        stillAuthorized.run();
        return enriched;
    }

    /** Granted course materials, for showing what a lesson conversation draws on. */
    public Result courseMaterials(Integer courseId) {
        return result(() -> {
            authorization.requireCourseRead(authorization.currentActor(), courseId);
            return jdbc.queryForList("SELECT d.id AS document_id,v.file_name AS title,g.document_version,"
                    + "CASE WHEN d.document_version=g.document_version AND d.active_index_version IS NOT NULL THEN 1 ELSE 0 END AS searchable "
                    + "FROM native_rag_course_grant g JOIN native_rag_document d ON d.id=g.document_id "
                    + "JOIN native_rag_document_version v ON v.document_id=g.document_id AND v.document_version=g.document_version "
                    + "WHERE g.course_id=? AND g.revoked_at IS NULL AND g.read_excerpt=TRUE ORDER BY v.file_name,d.id", courseId);
        });
    }

    private Map<String,String> courseSnapshot(Integer courseId) {
        var result=new java.util.TreeMap<String,String>();
        for(var row:jdbc.queryForList("SELECT d.id,d.active_index_version,d.document_version,d.parse_revision,g.document_version AS granted FROM native_rag_document d"
                +" JOIN native_rag_course_grant g ON g.document_id=d.id WHERE g.course_id=? AND g.revoked_at IS NULL AND g.read_excerpt=TRUE",courseId))
            result.put(text(row,"id")+":"+row.get("granted"),String.valueOf(row.get("active_index_version"))+":"+row.get("document_version")+":"+row.get("parse_revision"));
        return result;
    }

    /** Ranks authorized rows; {@code stillAuthorized} re-checks access and index stability around ranking. */
    private RagSearchResponse rank(String query,int limit,List<Map<String,Object>> rows,Runnable stillAuthorized,Integer visualActor) {
            if(rows.size()>MAX_SEARCH_CHUNKS)throw invalid("检索范围超过 2 万片段，请缩小知识库范围");
            var retrieved=retrieval.retrieve(query,rows,Math.max(1,Math.min(20,limit)));
            stillAuthorized.run();
            var numbered=new ArrayList<RagSearchHit>();
            for(var ranked:retrieved.ranked()) {
                var h=hit(ranked.row(),ranked.score());
                var numberedHit=new RagSearchHit(h.chunkId(),h.documentId(),h.kbId(),h.title(),h.content(),h.page(),h.ordinal(),h.score(),"["+(numbered.size()+1)+"]",h.visuals(),h.indexVersion(),h.documentVersion(),h.parseRevision());
                numbered.add(visualActor==null?numberedHit:withVisuals(numberedHit,visualActor));
            }
            stillAuthorized.run();
            String warning=retrieved.warning()==null?"":retrieved.warning();
            if(numbered.stream().anyMatch(h->!com.cupk.aiacademy.rag.formula.FormulaText.analyze(h.content()).blocks().isEmpty()))
                warning+=" 检索结果包含公式，请对照原页核验；可排版不代表识别准确。";
            var snapshots=new java.util.LinkedHashMap<String,RagIndexSnapshot>();
            for(var row:rows) {
                String id=text(row,"index_version");
                snapshots.putIfAbsent(id,new RagIndexSnapshot(text(row,"document_id"),id,((Number)row.get("document_version")).intValue(),((Number)row.get("parse_revision")).intValue(),textOrNull(row,"index_embedding_model"),((Number)row.get("index_dimensions")).intValue(),Boolean.TRUE.equals(row.get("index_normalized")) || row.get("index_normalized") instanceof Number n && n.intValue()!=0,text(row,"index_chunk_revision"),text(row,"index_parser_revision"),textOrNull(row,"index_embedding_fingerprint")));
            }
            return new RagSearchResponse(List.copyOf(numbered),"HYBRID".equals(retrieved.actualMode())?"hybrid":"bm25",warning,retrieved.actualMode(),retrieved.degradedReason(),List.copyOf(snapshots.values()));
    }

    private Map<String,String> activeSnapshot(List<String> ids) {
        String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        var result=new java.util.TreeMap<String,String>();
        for(var row:jdbc.queryForList("SELECT id,active_index_version,document_version,parse_revision FROM native_rag_document WHERE kb_id IN ("+placeholders+")",ids.toArray()))
            result.put(text(row,"id"),String.valueOf(row.get("active_index_version"))+":"+row.get("document_version")+":"+row.get("parse_revision"));
        return result;
    }

    private List<Map<String,Object>> rowsForDocument(String id) {
        return jdbc.queryForList("SELECT c.*,d.title FROM native_rag_chunk c JOIN native_rag_document d ON c.document_id=d.id WHERE c.document_id=? AND c.index_version=d.active_index_version ORDER BY c.ordinal_number", id);
    }

    private Map<String,Object> base(String id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM native_rag_base WHERE id=?", checkedId(id));
        if (rows.isEmpty()) throw new RagAccessException("404", "知识库不存在");
        return rows.getFirst();
    }

    private Map<String,Object> readable(String id, Integer user) {
        Map<String,Object> b = base(id);
        Integer boundCourse = courseKnowledge == null ? null : courseKnowledge.courseForBase(id);
        if (boundCourse == null || !courseKnowledge.isCourseManager(boundCourse, user))
            authorization.requireBaseOwner(authorization.actorForUser(user),((Number)b.get("owner_id")).intValue());
        return b;
    }

    private Map<String,Object> writable(String id, Integer user) {
        Map<String,Object> b = base(id);
        Integer boundCourse = courseKnowledge == null ? null : courseKnowledge.courseForBase(id);
        if (boundCourse == null || !courseKnowledge.isCourseManager(boundCourse, user))
            authorization.requireBaseOwner(authorization.actorForUser(user),((Number)b.get("owner_id")).intValue());
        return b;
    }

    private Map<String,Object> document(String id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM native_rag_document WHERE id=?", checkedId(id));
        if (rows.isEmpty()) throw new RagAccessException("404", "文档不存在");
        return rows.getFirst();
    }

    private Map<String,Object> readableDocument(String id, Integer user) {
        Map<String,Object> doc = document(id);
        readable(text(doc, "kb_id"), user);
        return doc;
    }

    private Map<String,Object> duplicate(String kbId, String hash) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM native_rag_document WHERE kb_id=? AND sha256=?", kbId, hash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String,Object> baseView(Map<String,Object> row, Integer user) {
        Map<String,Object> view = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "description", "owner_id", "created_at")) view.put(key, row.get(key));
        view.put("shared", bool(row.get("shared")));
        if (courseKnowledge != null) {
            Integer course = courseKnowledge.courseForBase(text(row,"id"));
            if (course == null) {
                var assignments = jdbc.queryForList("SELECT course_id FROM course_library_assignment WHERE legacy_kb_id=?", row.get("id"));
                if (!assignments.isEmpty()) course = ((Number)assignments.getFirst().get("course_id")).intValue();
            }
            if (course != null) view.put("course_id", course);
        }
        view.put("read_only", !user.equals(((Number)row.get("owner_id")).intValue()));
        view.put("knowledge_count", jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=?", Integer.class, row.get("id")));
        view.put("provider", "native");
        return view;
    }

    private Map<String,Object> documentView(Map<String,Object> row) {
        Map<String,Object> view = new LinkedHashMap<>();
        for (String key : List.of("id", "kb_id", "title", "file_name", "file_size", "sha256", "page_count", "chunk_count", "embedding_status", "document_version", "parse_revision", "active_index_version", "source_type", "mime_type", "processing_state", "processing_job_id", "processing_version", "created_at")) view.put(key, row.get(key));
        view.put("knowledge_base_id", row.get("kb_id"));
        String processing=text(row,"processing_state");
        if(processing.isBlank())processing="READY";
        String parseStatus="READY".equals(processing)?"success":processing.toLowerCase(java.util.Locale.ROOT);
        view.put("parse_status",parseStatus);view.put("parseStatus",parseStatus);
        view.put("documentId",row.get("id"));view.put("jobId",row.get("processing_job_id"));
        int progress=100;
        if(row.get("processing_job_id")!=null) {
            var jobs=jdbc.queryForList("SELECT progress FROM native_rag_ingestion_job WHERE id=?",row.get("processing_job_id"));
            if(!jobs.isEmpty())progress=((Number)jobs.getFirst().get("progress")).intValue();
        }
        view.put("parse_progress",progress);
        int pages = ((Number)row.get("page_count")).intValue();
        List<Integer> withoutText = new ArrayList<>();
        if (pages > 0) {
            List<Integer> indexedPages = jdbc.queryForList("SELECT DISTINCT page_number FROM native_rag_chunk WHERE document_id=? AND index_version=? AND page_number IS NOT NULL", Integer.class, row.get("id"),row.get("active_index_version"));
            var blanks=jdbc.queryForList("SELECT page_no FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision=? AND page_status='BLANK'",Integer.class,row.get("id"),row.get("document_version"),row.get("parse_revision"));
            for (int i = 1; i <= pages; i++) if (!indexedPages.contains(i) && !blanks.contains(i)) withoutText.add(i);
        }
        view.put("unindexed_pages", List.copyOf(withoutText));
        view.put("parse_warning",Set.of("QUEUED","PARSING","OCR","CHUNKING","EMBEDDING").contains(processing)
                ?"资料正在处理，完成的部分会陆续可用。":withoutText.isEmpty()?"":"部分页面没有取得文字，可在逐页结果中重试。");
        // Saved page edits whose text the published revision does not carry yet.
        Map<Integer,String> published=new HashMap<>();
        for(var page:jdbc.queryForList("SELECT page_no,chosen_text FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision=?",row.get("id"),row.get("document_version"),row.get("parse_revision")))
            published.put(((Number)page.get("page_no")).intValue(),text(page,"chosen_text"));
        Map<Integer,String> edited=new TreeMap<>();
        for(var edit:jdbc.queryForList("SELECT page_no,edited_text FROM native_rag_page_edit WHERE document_id=? AND document_version=? ORDER BY id",row.get("id"),row.get("document_version")))
            edited.put(((Number)edit.get("page_no")).intValue(),text(edit,"edited_text"));
        view.put("pending_edit_pages",edited.entrySet().stream().filter(edit->!edit.getValue().equals(published.get(edit.getKey()))).map(Map.Entry::getKey).toList());
        view.put("needs_review_pages",jdbc.queryForList("SELECT page_no FROM native_rag_page WHERE document_id=? AND document_version=? AND parse_revision=? AND needs_review=TRUE ORDER BY page_no",Integer.class,row.get("id"),row.get("document_version"),row.get("parse_revision")));
        return view;
    }

    private Map<String,Object> withDuplicate(Map<String,Object> view, boolean duplicate) { view.put("duplicate", duplicate); return view; }

    private RagSearchHit hit(Map<String,Object> row, double score) {
        Number page = (Number)row.get("page_number");
        int ordinal = ((Number)row.get("ordinal_number")).intValue();
        String title = text(row, "title");
        String citation = title + (page == null ? "，片段 " + ordinal : "，第 " + page.intValue() + " 页，片段 " + ordinal);
        return new RagSearchHit(text(row,"id"), text(row,"document_id"), text(row,"kb_id"), title,
                text(row,"content"), page == null ? null : page.intValue(), ordinal, score, citation,List.of(),textOrNull(row,"index_version"),row.get("document_version") instanceof Number v?v.intValue():null,row.get("parse_revision") instanceof Number r?r.intValue():null);
    }

    private RagSearchHit withVisuals(RagSearchHit hit,int actor) {
        if(hit.page()==null)return hit;
        return new RagSearchHit(hit.chunkId(),hit.documentId(),hit.kbId(),hit.title(),hit.content(),hit.page(),hit.ordinal(),hit.score(),hit.citation(),visuals.forHit(hit,actor),hit.indexVersion(),hit.documentVersion(),hit.parseRevision());
    }

    private String vectorJson(double[] vector) {
        try { return mapper.writeValueAsString(vector); }
        catch (Exception e) { throw new IllegalStateException("向量序列化失败"); }
    }

    private String textOrNull(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}

    private Integer owner() { return authorization.currentActor().userId(); }
    private Integer requireOwner(Integer id) { return authorization.actorForUser(id).userId(); }
    private String checkedId(String id) {
        try { if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); return id; }
        catch (IllegalArgumentException e) { throw invalid("非法资源 ID"); }
    }
    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) throw invalid("文件名不能为空");
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty() || name.length() > 255) throw invalid("文件名必须为 1–255 字");
        return name;
    }
    private String validateName(String name) { if (name == null || name.isBlank() || name.length() > 200) throw invalid("知识库名称必须为 1–200 字"); return name.strip(); }
    private String validateDescription(String description) { if (description != null && description.length() > 5000) throw invalid("描述不能超过 5000 字"); return description == null ? "" : description.strip(); }
    private String text(Map<String,Object> row, String key) { Object value = row.get(key); return value == null ? "" : value.toString(); }
    private boolean bool(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }
    private String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException("文件哈希失败"); } }
    private RagAccessException invalid(String message) { return new RagAccessException("400", message); }
    private Result result(Supplier<?> operation) {
        try { return Result.success(operation.get()); }
        catch (RagAccessException e) { return Result.error(e.code(), e.getMessage()); }
        catch (com.cupk.aiacademy.common.ForbiddenException e) { return Result.error("403",e.getMessage()); }
        catch (Exception e) { return Result.error("500", "本地知识库操作失败，请检查存储及数据库状态"); }
    }
}

