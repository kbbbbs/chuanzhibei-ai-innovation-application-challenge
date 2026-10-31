package com.cupk.aiacademy.rag.formula;

import java.util.*;
import java.util.regex.Pattern;

/** Bounded structural checks, not a proof of mathematical correctness. Offsets are UTF-16 source offsets. */
public final class FormulaText {
    public static final int VERSION=1;
    public static final int MAX_FORMULA_CHARS=12000;
    private static final Pattern ENV=Pattern.compile("\\\\(begin|end)\\{([a-zA-Z*]+)\\}");
    private static final Set<String> MATH_ENV=Set.of("equation","equation*","align","align*","aligned","gather","gather*",
            "gathered","multline","multline*","split","cases","matrix","pmatrix","bmatrix","Bmatrix","vmatrix","Vmatrix","array");
    private static final Pattern MATH_SIGNAL=Pattern.compile("[∑∏∫∂∇√∞≤≥≠±∈∀∃]|\\\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|partial|begin)\\b|[a-zA-Zα-ωΑ-Ω][a-zA-Z0-9_{}()\\s^.-]{0,25}[=<>][^=]");
    private FormulaText() { }
    public record Block(int ordinal,int start,int end,String raw,String latex,boolean display,String status,String errorCode) { }
    public record Analysis(List<Block> blocks,List<String> issues) {
        public boolean valid(){return issues.isEmpty();}
    }

    public static boolean suspectMath(String text){
        if(text==null)return false;
        // Scalar labels such as D=3 do not lose a fraction/matrix structure when stored as text.
        String candidates=text.replaceAll("\\b[A-Za-z][A-Za-z0-9_]*\\s*=\\s*[-+]?\\d+(?:\\.\\d+)?(?![\\d.])(?!\\s*[+*/^=−-])(?=$|[\\s，。,;；:：)\\]])","");
        return MATH_SIGNAL.matcher(candidates).find();
    }

    public static Analysis analyze(String text) {
        if(text==null || text.isEmpty())return new Analysis(List.of(),List.of());
        List<Block> blocks=new ArrayList<>();Set<String> issues=new LinkedHashSet<>();
        for(int i=0;i<text.length();) {
            int skipped=skipCode(text,i);if(skipped>i){i=skipped;continue;}
            if(escaped(text,i)){i++;continue;}
            String open=null,close=null;boolean display=false,environment=false;
            if(text.startsWith("$$",i)){open="$$";close="$$";display=true;}
            else if(text.startsWith("\\[",i)){open="\\[";close="\\]";display=true;}
            else if(text.startsWith("\\(",i)){open="\\(";close="\\)";}
            else if(text.charAt(i)=='$' && i+1<text.length() && !Character.isWhitespace(text.charAt(i+1)) && !currency(text,i)) {open="$";close="$";}
            else if(text.startsWith("\\begin{",i)) {
                var match=ENV.matcher(text);match.region(i,text.length());
                if(match.lookingAt() && MATH_ENV.contains(match.group(2))) {
                    open=match.group();close="\\end{"+match.group(2)+"}";display=true;environment=true;
                }
            }
            if(open==null){i++;continue;}
            int stop=closing(text,close,i+open.length());
            int end=stop<0?text.length():stop+close.length();
            String raw=text.substring(i,end);
            String latex=environment?raw:text.substring(i+open.length(),stop<0?end:stop);
            String error=stop<0?"FORMULA_UNCLOSED":check(latex);
            if(error!=null)issues.add(error);
            blocks.add(new Block(blocks.size()+1,i,end,raw,latex.strip(),display,error==null?"STRUCTURE_OK":"INVALID",error));
            i=end;
        }
        return new Analysis(List.copyOf(blocks),List.copyOf(issues));
    }

    /** An evidence budget may omit a whole formula, but must never fabricate a truncated one. */
    public static String safePrefix(String text,int limit) {
        if(text==null || limit<=0)return "";
        int end=Math.min(text.length(),limit);
        for(var block:analyze(text).blocks()) {
            if(block.start()>=end)break;
            if(!"STRUCTURE_OK".equals(block.status()) || block.end()>end){end=block.start();break;}
        }
        if(end>0 && end<text.length() && Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }

    private static String check(String latex) {
        if(latex.isBlank())return "FORMULA_EMPTY";
        if(latex.length()>MAX_FORMULA_CHARS)return "FORMULA_TOO_LONG";
        int braces=0;
        for(int i=0;i<latex.length();i++)if(!escaped(latex,i)) {
            char c=latex.charAt(i);
            if(c=='{')braces++;
            if(c=='}' && --braces<0)return "FORMULA_BRACES_UNBALANCED";
        }
        if(braces!=0)return "FORMULA_BRACES_UNBALANCED";
        var environments=new ArrayDeque<String>();var matches=ENV.matcher(latex);
        while(matches.find()) {
            if("begin".equals(matches.group(1)))environments.push(matches.group(2));
            else if(environments.isEmpty() || !environments.pop().equals(matches.group(2)))return "FORMULA_ENV_UNBALANCED";
        }
        if(!environments.isEmpty())return "FORMULA_ENV_UNBALANCED";
        long left=Pattern.compile("\\\\left\\b").matcher(latex).results().count();
        long right=Pattern.compile("\\\\right\\b").matcher(latex).results().count();
        if(left!=right)return "FORMULA_FENCES_UNBALANCED";
        return null;
    }
    private static int closing(String text,String marker,int from) {
        for(int at=text.indexOf(marker,from);at>=0;at=text.indexOf(marker,at+marker.length()))
            if(!escaped(text,at))return at;
        return -1;
    }
    private static boolean escaped(String text,int at) {
        int count=0;for(int i=at-1;i>=0 && text.charAt(i)=='\\';i--)count++;
        return count%2==1;
    }
    private static boolean currency(String text,int at) {
        int i=at+1;if(i>=text.length() || !Character.isDigit(text.charAt(i)))return false;
        while(i<text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i)=='.' || text.charAt(i)==','))i++;
        return i==text.length() || Character.isWhitespace(text.charAt(i));
    }
    private static int skipCode(String text,int at) {
        if(text.charAt(at)!='`' && !text.startsWith("~~~",at))return at;
        char c=text.charAt(at);int n=1;while(at+n<text.length() && text.charAt(at+n)==c)n++;
        String marker=text.substring(at,at+n);int close=text.indexOf(marker,at+n);
        return close<0?text.length():close+n;
    }
}
