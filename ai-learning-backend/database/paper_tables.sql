-- ============================================
-- 论文检索功能 - 数据库表结构
-- 适用于 MySQL 8.0+
-- ============================================

DROP TABLE IF EXISTS t_paper_author;
DROP TABLE IF EXISTS t_paper;

CREATE TABLE t_paper (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Internal primary key',
    arxiv_id VARCHAR(50) NOT NULL COMMENT 'arXiv paper ID (dedup key)',
    title VARCHAR(500) NOT NULL COMMENT 'Paper title',
    abstract_text TEXT COMMENT 'Abstract',
    year INT COMMENT 'Publication year',
    url VARCHAR(500) COMMENT 'Paper page URL',
    pdf_url VARCHAR(500) COMMENT 'Direct PDF URL',
    venue VARCHAR(500) COMMENT 'Journal/conference venue',
    citation_count INT DEFAULT 0 COMMENT 'Citation count',
    source VARCHAR(50) DEFAULT 'arxiv' COMMENT 'Data source',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arxiv_id (arxiv_id),
    FULLTEXT INDEX ft_title_abstract (title, abstract_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Locally stored academic papers';

CREATE TABLE t_paper_author (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Author primary key',
    paper_id INT NOT NULL COMMENT 'FK to t_paper.id',
    name VARCHAR(200) NOT NULL COMMENT 'Author display name',
    sort_order INT DEFAULT 0 COMMENT 'Author order in paper',
    INDEX idx_paper_id (paper_id),
    CONSTRAINT fk_pa_paper FOREIGN KEY (paper_id) REFERENCES t_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Paper authors';

CREATE TABLE paper_user_mark (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'User paper mark primary key',
    user_id INT NOT NULL COMMENT 'Current user id',
    paper_id VARCHAR(100) NOT NULL COMMENT 'External or local paper identifier',
    title VARCHAR(500) COMMENT 'Paper title snapshot',
    source VARCHAR(50) COMMENT 'Paper source snapshot',
    is_favorite TINYINT(1) DEFAULT 0 COMMENT 'Favorite flag',
    read_status VARCHAR(20) DEFAULT 'UNREAD' COMMENT 'Read status',
    note_md TEXT COMMENT 'Personal markdown note',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_paper (user_id, paper_id),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User paper favorites and notes';
