USE ai_academy;

SHOW TABLES LIKE 't_agent_pending_action';

DESC t_agent_pending_action;

SELECT id, code, enabled_tools
FROM t_agent
WHERE id = 1 OR code = 'learning_assistant';