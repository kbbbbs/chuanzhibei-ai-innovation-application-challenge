-- ============================================================
-- Paper & PaperMark tables for AI Academy
-- Run: mysql -u root -p ai_academy < database/paper_tables.sql
-- ============================================================

-- Paper repository (local cache of arXiv / OpenAlex / Semantic Scholar papers)
CREATE TABLE IF NOT EXISTS t_paper (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arxiv_id VARCHAR(64) NOT NULL COMMENT 'arXiv / external paper identifier',
    title VARCHAR(1024) NOT NULL COMMENT 'Paper title',
    abstract_text TEXT COMMENT 'Abstract text',
    year INT COMMENT 'Publication year',
    url VARCHAR(1024) COMMENT 'External URL (e.g. arXiv abstract page)',
    pdf_url VARCHAR(1024) COMMENT 'Direct PDF URL',
    venue VARCHAR(512) COMMENT 'Journal / conference / venue name',
    citation_count INT DEFAULT 0 COMMENT 'Citation count at time of import',
    source VARCHAR(64) DEFAULT 'arxiv' COMMENT 'Source: arxiv, openalex, semantic_scholar, local',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arxiv_id (arxiv_id),
    FULLTEXT INDEX ft_paper_title (title),
    FULLTEXT INDEX ft_paper_abstract (abstract_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Academic paper repository';

-- Paper authors
CREATE TABLE IF NOT EXISTS t_paper_author (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paper_id INT NOT NULL COMMENT 'FK to t_paper.id',
    name VARCHAR(255) NOT NULL COMMENT 'Author name',
    sort_order INT DEFAULT 0 COMMENT 'Author order in paper',
    CONSTRAINT fk_paper_author_paper FOREIGN KEY (paper_id) REFERENCES t_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Paper author associations';

-- Per-user marks on papers (favorites, read status, notes)
CREATE TABLE IF NOT EXISTS paper_user_mark (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT 'FK to t_user.id',
    paper_id VARCHAR(128) NOT NULL COMMENT 'Paper identifier (arxiv_id or external id)',
    title VARCHAR(1024) COMMENT 'Paper title (denormalized for quick display)',
    source VARCHAR(64) COMMENT 'Source identifier',
    is_favorite TINYINT(1) DEFAULT 0 COMMENT 'Bookmark flag',
    read_status VARCHAR(16) DEFAULT 'UNREAD' COMMENT 'READ or UNREAD',
    note_md TEXT COMMENT 'User notes in Markdown format',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_paper (user_id, paper_id),
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user paper marks (favorites, reading status, notes)';
