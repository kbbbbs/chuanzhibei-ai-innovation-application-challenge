package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Test-only, preregistered evaluation mechanics. No product ranking changes. */
final class K03TuningSupport {
 private K03TuningSupport() { }
 record Score(int rrfK,int candidateLimit,int knownSourceTop5Hits,int knownSourceTop1Hits,double mrrAt5) { }
 static Score select(List<Score> values) {
  return values.stream().min(Comparator.comparingInt(Score::knownSourceTop5Hits).reversed()
   .thenComparing(Comparator.comparingDouble(Score::mrrAt5).reversed())
   .thenComparing(Comparator.comparingInt(Score::knownSourceTop1Hits).reversed())
   .thenComparingInt(Score::candidateLimit).thenComparingInt(Score::rrfK)).orElseThrow();
 }
 static List<JsonNode> tuningQueries(JsonNode dataset,JsonNode plan) {
  var allowed=new LinkedHashSet<String>();for(var id:plan.path("allowedQuestionIds"))if(!allowed.add(id.asText()))throw new IllegalArgumentException("Duplicate preregistered question ID");
  var selected=new ArrayList<JsonNode>();
  for(var question:dataset.path("questions"))if(allowed.contains(question.path("id").asText())) {
   if(!question.path("split").asText().equals("tuning")||!question.path("answerable").asBoolean())throw new IllegalArgumentException("Only registered answerable tuning questions may be scored");
   selected.add(question);
  }
  if(selected.size()!=allowed.size()||selected.size()!=plan.path("questionCount").asInt()||!selected.stream().map(q->q.path("id").asText()).toList().equals(new ArrayList<>(allowed)))throw new IllegalArgumentException("Tuning question IDs differ from preregistration");
  return List.copyOf(selected);
 }
 static RagRetrievalService configure(NativeRagService nativeService,RagEmbeddingClient embeddings,ObjectMapper json,int k,int candidates) {
  var indexes=new IndexVersionService();
  var retrieval=new RagRetrievalService(embeddings,new JdbcRagVectorStore(json,indexes),indexes,k,candidates);
  nativeService.configureRetrieval(retrieval);return retrieval;
 }
 static String sha(byte[] bytes) {
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}
 }
 static String inputSha(String input){return sha(input.getBytes(StandardCharsets.UTF_8));}
 static double cosine(double[] a,double[] b) {
  if(a.length!=b.length)throw new IllegalArgumentException("Cannot compare dimensions");
  double dot=0,an=0,bn=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];an+=a[i]*a[i];bn+=b[i]*b[i];}
  return dot/Math.sqrt(an*bn);
 }

 static final class RealCache extends RagEmbeddingClient {
  final RagEmbeddingClient real;final ObjectMapper json;final Path file;final int dimensions;
  final Map<String,double[]> vectors=new TreeMap<>();final Map<String,Integer> originalFacadeInputCount=new HashMap<>();final Set<String> diskHashes=new HashSet<>();
  int realCalls,realInputs,reused,persistedReused,loadedVectors;boolean incompatibleDiskCache;
  RealCache(RagEmbeddingClient real,ObjectMapper json,Path file,int dimensions) throws Exception {
   super(false,"","","",json);this.real=real;this.json=json;this.file=file;this.dimensions=dimensions;
   if(!Files.exists(file))return;
   var stored=json.readTree(Files.readString(file));
   if(stored.path("version").asInt()!=1||!stored.path("fingerprint").asText().equals(real.fingerprint())||!stored.path("model").asText().equals(real.modelName())||stored.path("dimensions").asInt()!=dimensions||!stored.path("normalization").asText().equals("L2")||!stored.path("requestProtocol").asText().equals("plain-text/v1")){incompatibleDiskCache=true;return;}
   try {
    for(var item:stored.path("vectors")) {
     String hash=item.path("inputSha256").asText();if(!hash.matches("[a-f0-9]{64}")||vectors.containsKey(hash))throw new IllegalStateException();
     double[] value=json.convertValue(item.path("vector"),double[].class);validate(value);vectors.put(hash,value);
     originalFacadeInputCount.put(hash,item.path("realFacadeInputCount").asInt());diskHashes.add(hash);
    }
    loadedVectors=vectors.size();
   }catch(Exception e){throw new IllegalStateException("Invalid real-vector cache; cannot use it as evaluation evidence");}
  }
  @Override public boolean isConfigured(){return real.isConfigured();}@Override public boolean isRequested(){return true;}
  @Override public String fingerprint(){return real.fingerprint();}@Override public String modelName(){return real.modelName();}
  void validate(double[] vector) {
   if(vector==null||vector.length!=dimensions)throw new IllegalStateException("Incompatible real-vector dimensions");
   double norm=0;for(double value:vector){if(!Double.isFinite(value))throw new IllegalStateException("Invalid real-vector value");norm+=value*value;}
   if(!Double.isFinite(norm)||Math.abs(norm-1)>1e-6)throw new IllegalStateException("Real vectors must be L2-normalized");
  }
  void prime(List<String> values) {
   var missing=new LinkedHashMap<String,String>();for(String value:values){String hash=inputSha(value);if(!vectors.containsKey(hash))missing.putIfAbsent(hash,value);}
   if(missing.isEmpty())return;
   var result=real.embed(new ArrayList<>(missing.values()));realCalls++;realInputs+=missing.size();
   if(result.size()!=missing.size())throw new IllegalStateException("Incomplete real-vector prime");
   int i=0;for(String hash:missing.keySet()){double[] vector=result.get(i++);validate(vector);vectors.put(hash,vector.clone());originalFacadeInputCount.put(hash,missing.size());}
   persist();
  }
  void persist() {
   try {
    if(file.getParent()!=null)Files.createDirectories(file.getParent());
    var entries=vectors.entrySet().stream().map(e->Map.of("inputSha256",e.getKey(),"vector",e.getValue(),"realFacadeInputCount",originalFacadeInputCount.getOrDefault(e.getKey(),0))).toList();
    var stored=new LinkedHashMap<String,Object>();stored.put("version",1);stored.put("model",modelName());stored.put("fingerprint",fingerprint());stored.put("dimensions",dimensions);stored.put("normalization","L2");stored.put("requestProtocol","plain-text/v1");stored.put("vectors",entries);
    Path pending=file.resolveSibling(file.getFileName()+".pending");Files.writeString(pending,json.writeValueAsString(stored));
    try{Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);}
   }catch(Exception e){throw new IllegalStateException("Cannot persist real-vector evidence cache");}
  }
  @Override public List<double[]> embed(List<String> values) {
   for(String value:values)if(vectors.containsKey(inputSha(value))){reused++;if(diskHashes.contains(inputSha(value)))persistedReused++;}
   prime(values);return values.stream().map(value->vectors.get(inputSha(value)).clone()).toList();
  }
 }
}
