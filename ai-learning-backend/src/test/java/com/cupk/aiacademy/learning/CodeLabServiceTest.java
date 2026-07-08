package com.cupk.aiacademy.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeLabServiceTest {
    private final CodeLabService service = new CodeLabService();

    @Test
    void runsJavaMainAndReturnsStdout() {
        CodeLabRunRequest request = new CodeLabRunRequest();
        request.setLanguage("java");
        request.setCode("""
                public class Main {
                  public static void main(String[] args) {
                    System.out.println("Amazing Teaching IDE ready");
                  }
                }
                """);

        CodeLabRunResponse response = service.run(request);

        assertEquals(0, response.getExitCode());
        assertTrue(response.getStdout().contains("Amazing Teaching IDE ready"));
        assertEquals("", response.getStderr());
    }

    @Test
    void returnsCompilerErrorForInvalidJavaInsteadOfCrashing() {
        CodeLabRunRequest request = new CodeLabRunRequest();
        request.setLanguage("java");
        request.setCode("""
                public class Main {
                  public static void main(String[] args) {
                    System.out.println("missing semicolon")
                  }
                }
                """);

        CodeLabRunResponse response = service.run(request);

        assertTrue(response.getExitCode() != 0);
        assertTrue(!response.getStderr().isBlank());
    }

    @Test
    void rejectsUnsupportedLanguageWithFriendlyMessage() {
        CodeLabRunRequest request = new CodeLabRunRequest();
        request.setLanguage("ruby");
        request.setCode("puts 'hi'");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.run(request));

        assertTrue(error.getMessage().contains("暂不支持该语言"));
    }
}
