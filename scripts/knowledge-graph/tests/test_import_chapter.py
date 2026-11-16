import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch


MODULE = Path(__file__).resolve().parents[1] / 'import_chapter.py'
spec = importlib.util.spec_from_file_location('import_chapter', MODULE)
chapter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(chapter)


class ChapterImportTest(unittest.TestCase):
    def test_evidence_uses_actual_chunk_and_exact_quote(self):
        source = [{'id': 'real-chunk', 'page': 31, 'content': '监督学习可分为回归问题、分类问题和结构化学习问题。'}]
        found = chapter.find_evidence(source, 31, '监督学习')
        self.assertEqual('real-chunk', found['chunkId'])
        self.assertIn(found['quote'], source[0]['content'])

    def test_evidence_cannot_silently_use_another_page(self):
        with self.assertRaisesRegex(ValueError, '31'):
            chapter.find_evidence([{'id': 'wrong-page', 'page': 5, 'content': '监督学习'}], 31, '监督学习')

    def test_missing_keyword_stops_candidate_generation(self):
        with self.assertRaises(ValueError):
            chapter.find_evidence([{'id': 'real', 'page': 31, 'content': '本页没有所选概念。'}], 31, '监督学习')

    def test_wrong_pdf_cannot_receive_this_curated_snapshot(self):
        with self.assertRaisesRegex(ValueError, 'SHA'):
            chapter.require_source_hash(b'other course document')

    def test_chinese_search_is_encoded_before_http_request(self):
        with patch.object(chapter.urllib.request, 'urlopen') as opened:
            opened.return_value.__enter__.return_value.read.return_value = b'{"code":"200","data":[]}'
            chapter.Api('http://127.0.0.1:8080').request('GET', '/api/rag/graphs/g/entities?keyword=分类')
            request = opened.call_args.args[0]
            self.assertIn('keyword=%E5%88%86%E7%B1%BB', request.full_url)


if __name__ == '__main__':
    unittest.main()
