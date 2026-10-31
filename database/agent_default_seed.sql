-- 为已经完成建表、但 t_agent 仍为空的现有数据库补充默认学习助教。
-- 可重复执行：若 learning_assistant 已存在（包括停用状态），不会覆盖管理员配置。

INSERT INTO `t_agent` (
  `code`,
  `name`,
  `description`,
  `system_prompt`,
  `model`,
  `temperature`,
  `knowledge_base_ids`,
  `enabled_tools`,
  `status`,
  `create_time`,
  `update_time`
)
SELECT
  'learning_assistant',
  '通用学习助教',
  '面向学生的通用学习答疑 Agent',
  '你是 AI 智学平台里的学习助教。请用清晰、准确、友好的中文回答，必要时分步骤说明。遇到不确定的信息，请明确说明不确定。',
  NULL,
  0.70,
  NULL,
  NULL,
  1,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1
  FROM `t_agent`
  WHERE `code` = 'learning_assistant'
);
