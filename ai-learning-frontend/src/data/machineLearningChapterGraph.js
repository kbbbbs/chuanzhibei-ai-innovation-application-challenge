// Curated chapter demonstration, not an extraction result for an arbitrary private knowledge base.
// Source checked on 2026-11-15. Pages are 1-based PDF pages, not inferred slide headings.
export const chapterGraph = {
  id: 'ml-overview-chapter-2',
  title: '机器学习概述',
  course: '人工智能基础',
  chapter: '第2章',
  source: {
    title: '第2章-机器学习概述-okk.pdf',
    pageCount: 36,
    sha256: '61d9f7c56111ce8ea07e6581fca828c09af5e4b8f95e0836de9b0f3d8d49d52f',
  },
  nodes: [
    { id: 'ml', label: '机器学习', group: 'overview', section: '章节概览', pages: [3, 7], position: { x: 30, y: 340 }, description: '从观测样本中学习规律，用得到的模型对新的输入进行预测。本图选取本章的核心概念与代表性学习类型。' },
    { id: 'basic', label: '基本概念', group: 'basic', section: '2.1 基本概念', pages: [5, 7], position: { x: 280, y: 65 }, description: '先认识特征、标签、样本与数据集，再理解训练和预测的基本流程。' },
    { id: 'elements', label: '三个基本要素', group: 'elements', section: '2.2 三个基本要素', pages: [14], position: { x: 280, y: 340 }, description: '本章将模型、学习准则、优化算法作为机器学习的三个基本要素，分别回答用什么表示、如何评价以及如何求解。' },
    { id: 'types', label: '学习类型', group: 'types', section: '2.3 学习算法的类型', pages: [13, 31], position: { x: 280, y: 635 }, description: '根据训练样本提供的信息和反馈方式区分学习类型。这里展示监督学习与无监督学习两个代表分支，并非本章全部类型。' },
    { id: 'features', label: '特征与标签', group: 'basic', section: '2.1 基本概念', pages: [5], position: { x: 555, y: 10 }, description: '特征描述样本的属性，可以组成特征向量；标签表示预测目标，可以是连续值或离散类别。' },
    { id: 'dataset', label: '样本与数据集', group: 'basic', section: '2.1 基本概念', pages: [5, 7], position: { x: 555, y: 120 }, description: '数据集由样本组成。训练集用于学习模型，测试集用于检验模型在未参与训练的数据上的表现。' },
    { id: 'model', label: '模型', group: 'elements', section: '2.2 三个基本要素', pages: [14, 15], position: { x: 555, y: 230 }, description: '模型表示输入与输出之间的映射。本章介绍线性模型和非线性模型，学习过程需要确定模型的参数。' },
    { id: 'criterion', label: '学习准则', group: 'elements', section: '2.2 三个基本要素', pages: [16, 19], position: { x: 555, y: 340 }, description: '学习准则规定怎样评价模型。本章从损失函数和风险出发，引出通过最小化训练集平均损失学习参数的经验风险最小化准则。' },
    { id: 'optimization', label: '优化算法', group: 'elements', section: '2.2 三个基本要素', pages: [14, 24], position: { x: 555, y: 450 }, description: '在模型与学习准则确定后，通过优化算法寻找参数。本章介绍梯度下降及其变体。' },
    { id: 'supervised', label: '监督学习', group: 'types', section: '2.3 学习算法的类型', pages: [31], position: { x: 555, y: 585 }, description: '训练样本带有标签，学习特征与标签之间的关系。按照标签类型，可进一步区分回归、分类等问题。' },
    { id: 'unsupervised', label: '无监督学习', group: 'types', section: '2.3 学习算法的类型', pages: [13], position: { x: 555, y: 695 }, description: '本章用图像聚类说明无监督学习：样本没有类别标签，算法依据图片之间的相似性进行分组。' },
    { id: 'loss', label: '损失函数', group: 'elements', section: '2.2 三个基本要素', pages: [17, 18], position: { x: 830, y: 340 }, description: '用非负实数衡量模型预测与真实标签之间的差异。本章介绍 0-1 损失、平方损失和交叉熵损失。' },
    { id: 'gradient', label: '梯度下降', group: 'elements', section: '2.2 三个基本要素', pages: [24, 26], position: { x: 830, y: 450 }, description: '从初始参数出发，迭代调整参数以降低风险函数。学习率控制更新步长，取值会影响优化的速度和稳定性。' },
    { id: 'classification', label: '分类', group: 'types', section: '2.3 学习算法的类型', pages: [11, 12, 31], position: { x: 830, y: 585 }, description: '预测离散类别，例如手写数字识别和垃圾邮件检测。本章将其列为监督学习中的一种问题。' },
    { id: 'regression', label: '回归', group: 'types', section: '2.3 学习算法的类型', pages: [9, 10, 31], position: { x: 830, y: 695 }, description: '预测连续数值，例如电影票房和房价。本章将其列为监督学习中的一种问题。' },
  ],
  relations: [
    { id: 'ml-basic', source: 'ml', target: 'basic', label: '章节内容', kind: 'knowledge', pages: [2, 34], description: '课程目录将基本概念列为本章第一部分。' },
    { id: 'ml-elements', source: 'ml', target: 'elements', label: '章节内容', kind: 'knowledge', pages: [2, 34], description: '课程目录将三个基本要素列为本章第二部分。' },
    { id: 'ml-types', source: 'ml', target: 'types', label: '章节内容', kind: 'knowledge', pages: [2, 34], description: '课程目录将学习算法的类型列为本章第三部分。' },
    { id: 'basic-features', source: 'basic', target: 'features', label: '包含', kind: 'knowledge', pages: [5], description: '基本概念部分用芒果示例介绍特征、特征向量与标签。' },
    { id: 'basic-dataset', source: 'basic', target: 'dataset', label: '包含', kind: 'knowledge', pages: [5], description: '基本概念部分定义样本、数据集、训练集和测试集。' },
    { id: 'elements-model', source: 'elements', target: 'model', label: '包含', kind: 'knowledge', pages: [14], description: '模型是本章明确列出的三个基本要素之一。' },
    { id: 'elements-criterion', source: 'elements', target: 'criterion', label: '包含', kind: 'knowledge', pages: [14], description: '学习准则是本章明确列出的三个基本要素之一。' },
    { id: 'elements-optimization', source: 'elements', target: 'optimization', label: '包含', kind: 'knowledge', pages: [14], description: '优化算法是本章明确列出的三个基本要素之一。' },
    { id: 'types-supervised', source: 'types', target: 'supervised', label: '类型示例', kind: 'knowledge', pages: [31], description: '本章按样本提供的信息和反馈方式介绍监督学习。' },
    { id: 'types-unsupervised', source: 'types', target: 'unsupervised', label: '类型示例', kind: 'knowledge', pages: [13], description: '本章将无标签的图像聚类归为无监督学习问题。' },
    { id: 'criterion-loss', source: 'criterion', target: 'loss', label: '包含', kind: 'knowledge', pages: [14, 17, 18], description: '学习准则部分介绍损失函数，用于量化预测和真实标签之间的差异。' },
    { id: 'optimization-gradient', source: 'optimization', target: 'gradient', label: '算法示例', kind: 'knowledge', pages: [14, 24], description: '梯度下降是课件介绍的优化算法之一。' },
    { id: 'supervised-classification', source: 'supervised', target: 'classification', label: '任务类型', kind: 'knowledge', pages: [31], description: '监督学习按标签类型可分为分类、回归等问题。分类预测离散类别。' },
    { id: 'supervised-regression', source: 'supervised', target: 'regression', label: '任务类型', kind: 'knowledge', pages: [31], description: '监督学习按标签类型可分为分类、回归等问题。回归预测连续数值。' },
    { id: 'dataset-model', source: 'dataset', target: 'model', label: '建议先学', kind: 'learning', pages: [5, 7, 15], description: '建议先认识样本和数据集，再理解模型要学习的输入输出关系。这是本示范的学习顺序建议，待教师确认。' },
    { id: 'model-criterion', source: 'model', target: 'criterion', label: '建议先学', kind: 'learning', pages: [15, 16], description: '建议先明确模型及其预测，再学习怎样评价预测。这是本示范的学习顺序建议，待教师确认。' },
    { id: 'loss-gradient', source: 'loss', target: 'gradient', label: '建议先学', kind: 'learning', pages: [17, 19, 24], description: '建议先理解损失与优化目标，再学习梯度下降如何调整参数。这是本示范的学习顺序建议，待教师确认。' },
  ],
}

export function formatPages(pages) {
  const sorted = [...new Set(pages)].sort((a, b) => a - b)
  const ranges = []
  for (let i = 0; i < sorted.length; i++) {
    const start = sorted[i]
    let end = start
    while (sorted[i + 1] === end + 1) end = sorted[++i]
    ranges.push(start === end ? String(start) : `${start}–${end}`)
  }
  return `第 ${ranges.join('、')} 页`
}
