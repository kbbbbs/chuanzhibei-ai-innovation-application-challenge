package com.cupk.aiacademy.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LessonSummaryTest {
    @Test
    void skipsQuotesHeadingsAndMathToFindTheFirstProseParagraph() {
        String markdown = "> 本页为演示内容。\n\n## 监督学习\n\n给定训练集 $\\{(x_i, y_i)\\}$，学习一个函数。\n\n"
                + "## 均方误差\n\n回归任务常用均方误差（MSE）作为损失函数：\n\n$$L(\\theta) = \\frac{1}{n}$$\n";
        assertEquals("回归任务常用均方误差（MSE）作为损失函数：", LearningContentRepository.markdownSummary(markdown, 120));
    }

    @Test
    void stripsEmphasisCodeAndLinkSyntax() {
        String markdown = "## 定义\n\n人工智能研究如何让机器表现出**感知、推理**等行为，参见[教材](https://example.com)与 `AI`。";
        assertEquals("人工智能研究如何让机器表现出感知、推理等行为，参见教材与 AI。",
                LearningContentRepository.markdownSummary(markdown, 120));
    }

    @Test
    void dropsInlineMathWhenEveryParagraphContainsSome() {
        assertEquals("沿梯度反方向更新参数。", LearningContentRepository.markdownSummary("沿梯度反方向更新参数 $\\theta$。", 120));
    }

    @Test
    void fallsBackToTheFirstLineWithoutMarkersWhenThereIsNoProse() {
        assertEquals("仅有一句引用", LearningContentRepository.markdownSummary("> 仅有一句引用", 120));
        assertEquals("", LearningContentRepository.markdownSummary("  ", 120));
        assertEquals("", LearningContentRepository.markdownSummary(null, 120));
    }

    @Test
    void truncatesWithAnEllipsis() {
        assertEquals("一二三四…", LearningContentRepository.markdownSummary("一二三四五六七八九十", 5));
    }
}
