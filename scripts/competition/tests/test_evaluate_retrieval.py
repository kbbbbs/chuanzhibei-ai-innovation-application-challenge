import importlib.util,json,hashlib,tempfile,unittest
from pathlib import Path
SCRIPT=Path(__file__).resolve().parents[1]/'evaluate_retrieval.py'
spec=importlib.util.spec_from_file_location('evaluation',SCRIPT);evaluation=importlib.util.module_from_spec(spec);spec.loader.exec_module(evaluation)

class RetrievalMetricTests(unittest.TestCase):
 def fixture(self,root):
  data={'sources':[{'id':'s1','sourceFile':'chapter.pdf','sourcePage':14}], 'questions':[
   {'id':'t','split':'tuning','answerable':True,'goldSourceIds':['s1']},
   {'id':'a','split':'acceptance','answerable':True,'goldSourceIds':['s1']},
   {'id':'n','split':'acceptance','answerable':False,'goldSourceIds':[]}]}
  dataset=root/'dataset.json';dataset.write_text(json.dumps(data),encoding='utf-8')
  report={'datasetSha256':hashlib.sha256(dataset.read_bytes()).hexdigest(),'questions':[
   {'id':'t','hybridSources':['chapter-p14'],'bm25Sources':[]},
   {'id':'a','hybridSources':['other-p1','chapter-p14'],'bm25Sources':['chapter-p14']},
   {'id':'n','hybridSources':['other-p1'],'bm25Sources':[]}]}
  result=root/'report.json';result.write_text(json.dumps(report),encoding='utf-8');return dataset,result
 def test_acceptance_metrics_exclude_tuning_and_no_answer_from_hit_denominator(self):
  with tempfile.TemporaryDirectory() as d:
   dataset,report=self.fixture(Path(d));result=evaluation.evaluate(dataset,report)
   self.assertEqual(1,result['acceptance']['answerable']);self.assertEqual(1.0,result['acceptance']['hybrid']['knownSourceTop5Rate'])
   self.assertEqual(.5,result['acceptance']['hybrid']['mrrAt5']);self.assertEqual(1,result['acceptance']['noAnswerQueriesWithHybridCandidates'])
   self.assertFalse(result['abstentionEvaluated'])
 def test_missing_queries_cannot_silently_improve_metrics(self):
  with tempfile.TemporaryDirectory() as d:
   dataset,report=self.fixture(Path(d));data=json.loads(report.read_text());data['questions'].pop(1);report.write_text(json.dumps(data))
   with self.assertRaises(ValueError):evaluation.evaluate(dataset,report)
 def test_frozen_dataset_hash_must_match_actual_input(self):
  with tempfile.TemporaryDirectory() as d:
   dataset,report=self.fixture(Path(d));dataset.write_text(dataset.read_text()+'\n')
   with self.assertRaises(ValueError):evaluation.evaluate(dataset,report)

if __name__=='__main__':unittest.main()
