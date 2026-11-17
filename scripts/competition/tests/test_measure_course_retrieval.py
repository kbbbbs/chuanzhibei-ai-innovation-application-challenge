import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / 'measure_course_retrieval.py'
SPEC = importlib.util.spec_from_file_location('course_measurement', SCRIPT)
MEASUREMENT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MEASUREMENT)


class CourseRetrievalMeasurementTests(unittest.TestCase):
    def fixture(self):
        questions = [{'id': f'q{i:02}', 'goldPages': [i]} for i in range(1, 21)]
        rows = [
            {'id': question['id'],
             'hybrid': {'actualMode': 'HYBRID', 'pages': [99, i], 'elapsedMs': 10},
             'keyword': {'actualMode': 'KEYWORD', 'pages': [i], 'elapsedMs': 1}}
            for i, question in enumerate(questions, 1)
        ]
        return {'questions': questions}, {'questions': rows}

    def test_reports_observed_metrics_without_quality_threshold(self):
        dataset, report = self.fixture()
        result = MEASUREMENT.summarize(dataset, report)
        self.assertEqual(20, result['questionCount'])
        self.assertEqual(20, result['hybrid']['top5Hits'])
        self.assertEqual(0, result['hybrid']['top1Hits'])
        self.assertEqual(0.5, result['hybrid']['mrrAt5'])
        self.assertEqual(20, result['keyword']['top1Hits'])
        for row in report['questions']:
            row['hybrid']['pages'] = []
        self.assertEqual(0.0, MEASUREMENT.summarize(dataset, report)['hybrid']['top5Rate'])

    def test_degradation_and_failure_remain_in_the_denominator(self):
        dataset, report = self.fixture()
        report['questions'][0]['hybrid']['actualMode'] = 'KEYWORD'
        report['questions'][1]['hybrid'] = {'actualMode': 'ERROR', 'pages': [], 'errorCode': '503'}
        result = MEASUREMENT.summarize(dataset, report)['hybrid']
        self.assertEqual(20, result['attempted'])
        self.assertEqual(18, result['top5Hits'])
        self.assertEqual(0.9, result['top5Rate'])
        self.assertEqual(1, result['degraded'])
        self.assertEqual(1, result['failed'])

    def test_missing_or_duplicate_results_are_rejected(self):
        dataset, report = self.fixture()
        report['questions'].pop()
        with self.assertRaises(ValueError):
            MEASUREMENT.summarize(dataset, report)
        dataset, report = self.fixture()
        report['questions'][-1]['id'] = report['questions'][0]['id']
        with self.assertRaises(ValueError):
            MEASUREMENT.summarize(dataset, report)


if __name__ == '__main__':
    unittest.main()
