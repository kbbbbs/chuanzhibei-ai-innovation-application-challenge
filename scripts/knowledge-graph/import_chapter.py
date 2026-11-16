"""Import reviewed chapter notes as a draft using real, authorized RAG chunks."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
import urllib.parse
import uuid


CURATED_SHA = '61d9f7c56111ce8ea07e6581fca828c09af5e4b8f95e0836de9b0f3d8d49d52f'

# These statements were checked against PDF pages. They remain candidates for the
# course teacher to review and publish through the application.
CONCEPTS = [
    ('feature', '特征', 'CONCEPT', 5, '特征', '描述样本属性，如颜色、大小和形状。'),
    ('label', '标签', 'CONCEPT', 5, '标签', '表示预测目标，可以是连续值或离散类别。'),
    ('sample', '样本', 'CONCEPT', 5, '样本', '具有特征及标签的一个观测对象。'),
    ('dataset', '数据集', 'CONCEPT', 5, '数据集', '由一组样本组成的集合。'),
    ('training', '训练集', 'CONCEPT', 5, '训练集', '其中的样本用于训练模型。'),
    ('test', '测试集', 'CONCEPT', 5, '测试集', '其中的样本用于检验模型。'),
    ('model', '模型', 'MODEL', 15, '模型', '表示输入与输出之间的映射。'),
    ('linear', '线性模型', 'MODEL', 15, '线性模型', '参数包括权重向量与偏置。'),
    ('nonlinear', '非线性模型', 'MODEL', 15, '非线性模型', '通过非线性基函数表示映射。'),
    ('criterion', '学习准则', 'CONCEPT', 14, '学习准则', '规定学习中使用的评价和优化目标。'),
    ('loss', '损失函数', 'METRIC', 17, '损失函数', '量化模型预测与真实标签之间的差异。'),
    ('zero-one', '0-1损失', 'METRIC', 17, '0-1', '用预测错误情况衡量模型。'),
    ('squared', '平方损失', 'METRIC', 17, '平方损失', '经常用于标签为实数值的预测任务。'),
    ('crossentropy', '交叉熵损失', 'METRIC', 18, '交叉熵损失', '一般用于分类问题，衡量真实与预测分布的差异。'),
    ('risk', '经验风险最小化', 'CONCEPT', 19, '经验风险', '通过最小化训练集上的平均损失学习参数。'),
    ('optimization', '优化算法', 'CONCEPT', 14, '优化算法', '用于求解学习目标的算法。'),
    ('gradient', '梯度下降', 'ALGORITHM', 24, '梯度下降', '初始化参数后迭代寻找风险函数的较小值。'),
    ('sgd', '随机梯度下降', 'ALGORITHM', 14, '随机梯度下降', '本章列举的优化算法。'),
    ('minibatch', '小批量梯度下降', 'ALGORITHM', 14, '小批量梯度下降', '本章列举的优化算法。'),
    ('learningrate', '学习率', 'CONCEPT', 24, '学习率', '控制参数更新的搜索步长。'),
    ('supervised', '监督学习', 'CONCEPT', 31, '监督学习', '利用带标签的训练样本学习特征和标签之间的关系。'),
    ('classification', '分类', 'TASK', 31, '分类', '预测离散类别的监督学习任务。'),
    ('regression', '回归', 'TASK', 31, '回归', '预测连续数值的监督学习任务。'),
    ('structured', '结构化学习', 'TASK', 31, '结构化学习', '本章列举的监督学习任务类型。'),
]

# source, target, type, label, page, evidence keyword, explanation
RELATIONS = [
    ('sample', 'feature', 'RELATED_TO', '具有特征', 5, '样本', '样本由特征及标签描述。'),
    ('sample', 'label', 'RELATED_TO', '具有标签', 5, '样本', '课件示例中的样本具有标签。'),
    ('dataset', 'sample', 'RELATED_TO', '由样本组成', 5, '数据集', '一组样本构成数据集。'),
    ('training', 'dataset', 'IS_A', '属于', 5, '训练集', '数据集一般划分为训练集与测试集。'),
    ('test', 'dataset', 'IS_A', '属于', 5, '测试集', '数据集一般划分为训练集与测试集。'),
    ('training', 'model', 'RELATED_TO', '用于训练', 5, '训练集', '训练集中的样本用于训练模型。'),
    ('test', 'model', 'RELATED_TO', '用于检验', 5, '测试集', '测试集中的样本用于检验模型。'),
    ('linear', 'model', 'IS_A', '模型类型', 15, '线性模型', '课件将模型分为线性与非线性模型。'),
    ('nonlinear', 'model', 'IS_A', '模型类型', 15, '非线性模型', '课件将模型分为线性与非线性模型。'),
    ('criterion', 'loss', 'RELATED_TO', '采用损失评价', 14, '损失函数', '损失函数是学习准则介绍的内容。'),
    ('criterion', 'risk', 'RELATED_TO', '采用风险准则', 19, '经验风险', '经验风险最小化是本章介绍的学习准则。'),
    ('zero-one', 'loss', 'IS_A', '损失类型', 17, '0-1', '0-1损失是本章介绍的损失函数。'),
    ('squared', 'loss', 'IS_A', '损失类型', 17, '平方损失', '平方损失是本章介绍的损失函数。'),
    ('crossentropy', 'loss', 'IS_A', '损失类型', 18, '交叉熵损失', '交叉熵损失是本章介绍的损失函数。'),
    ('model', 'loss', 'MEASURED_BY', '预测误差衡量', 17, '损失函数', '损失量化模型预测与真实标签的差异。'),
    ('crossentropy', 'classification', 'USED_FOR', '一般用于', 18, '分类', '课件说明交叉熵损失一般用于分类问题。'),
    ('risk', 'loss', 'RELATED_TO', '基于平均损失', 19, '平均损失', '经验风险是训练集上的平均损失。'),
    ('optimization', 'gradient', 'HAS_EXAMPLE', '算法示例', 14, '梯度下降法', '梯度下降是本章列举的优化算法。'),
    ('optimization', 'sgd', 'HAS_EXAMPLE', '算法示例', 14, '随机梯度下降', '随机梯度下降是本章列举的优化算法。'),
    ('optimization', 'minibatch', 'HAS_EXAMPLE', '算法示例', 14, '小批量梯度下降', '小批量梯度下降是本章列举的优化算法。'),
    ('gradient', 'learningrate', 'RELATED_TO', '使用步长', 24, '学习率', '梯度下降的搜索步长称为学习率。'),
    ('supervised', 'classification', 'HAS_TASK', '任务类型', 31, '分类', '监督学习按标签类型分为分类等问题。'),
    ('supervised', 'regression', 'HAS_TASK', '任务类型', 31, '回归', '监督学习按标签类型分为回归等问题。'),
    ('supervised', 'structured', 'HAS_TASK', '任务类型', 31, '结构化学习', '结构化学习是本章列出的监督学习任务。'),
    ('supervised', 'label', 'RELATED_TO', '训练样本带标签', 31, '标签', '监督学习的训练集中每个样本都有标签。'),
    ('supervised', 'feature', 'RELATED_TO', '学习特征关系', 31, '特征', '监督学习建模样本特征和标签之间的关系。'),
]


def require_source_hash(content):
    if hashlib.sha256(content).hexdigest() != CURATED_SHA:
        raise ValueError('SHA-256 不匹配，此候选整理仅适用于已核对的第2章课件。')


def find_evidence(chunks, page, keyword):
    for chunk in chunks:
        if chunk.get('pageNo', chunk.get('page')) != page:
            continue
        content = chunk.get('content', '')
        if keyword in content:
            start = max(0, content.index(keyword) - 40)
            return {'chunkId': chunk.get('chunkId', chunk.get('id')), 'quote': content[start:start + 220]}
    raise ValueError(f'第 {page} 页未找到依据 {keyword}，请先核查该页解析和切片。')


def build_snapshot(chunks):
    nodes = []
    for key, name, kind, page, keyword, description in CONCEPTS:
        evidence = find_evidence(chunks, page, keyword)
        nodes.append({'id': 'n-' + key, 'name': name, 'type': kind, 'description': description,
                      'aliases': [], 'evidenceIds': [evidence['chunkId']]})
    edges = []
    for index, (source, target, kind, label, page, keyword, explanation) in enumerate(RELATIONS):
        evidence = find_evidence(chunks, page, keyword)
        edges.append({'id': f'e-{index + 1}', 'source': 'n-' + source, 'target': 'n-' + target,
                      'type': kind, 'label': label, 'explanation': explanation, 'origin': 'DOCUMENT',
                      'evidenceIds': [evidence['chunkId']]})
    return {'nodes': nodes, 'edges': edges}


class Api:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.token = None

    def request(self, method, path, body=None, content_type='application/json'):
        headers = {'Content-Type': content_type}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        url = self.base + urllib.parse.quote(path, safe='/?=&%')
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                result = json.load(response)
        except urllib.error.HTTPError as failure:
            # Never print authorization headers or a raw login response.
            raise RuntimeError(f'请求失败 {method} {path}，HTTP {failure.code}') from None
        if str(result.get('code', '200')) != '200' or result.get('success') is False:
            raise RuntimeError(result.get('msg', result.get('message', '请求失败')))
        return result.get('data')

    def upload(self, library, source):
        boundary = uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="chapter2.pdf"\r\n'
                'Content-Type: application/pdf\r\n\r\n').encode() + source + f'\r\n--{boundary}--\r\n'.encode()
        return self.request('POST', f'/api/rag/knowledge-bases/{library}/documents', body,
                            f'multipart/form-data; boundary={boundary}')


def main():
    parser = argparse.ArgumentParser(description='将已核对的机器学习第2章整理导入为待审核图谱')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--username', required=True)
    parser.add_argument('--pdf', type=Path, required=True)
    parser.add_argument('--course-id', type=int, required=True)
    parser.add_argument('--chapter-id', type=int, required=True)
    parser.add_argument('--document-id', help='复用已经上传且属于当前教师的资料')
    args = parser.parse_args()
    source = args.pdf.read_bytes()
    require_source_hash(source)
    api = Api(args.base_url)
    password = os.environ.get('GRAPH_IMPORT_PASSWORD') or getpass.getpass('教师登录密码 ')
    login = api.request('POST', '/auth/login', {'username': args.username, 'password': password})
    api.token = login['token']
    document = args.document_id
    if not document:
        library = api.request('POST', '/api/rag/knowledge-bases', {'name': '章节知识图谱资料', 'description': '第2章课件'})
        uploaded = api.upload(library['id'], source)
        document = uploaded['id']
    deadline = time.monotonic() + 600
    while True:
        info = api.request('GET', f'/api/rag/documents/{document}')
        if info.get('active_index_version'):
            break
        if info.get('processing_state') in ('FAILED', 'CANCELLED') or time.monotonic() >= deadline:
            raise RuntimeError('资料解析未就绪，请在资料处理页面核查后重试。')
        time.sleep(2)
    if info.get('sha256') != CURATED_SHA:
        raise ValueError('已上传资料 SHA-256 与核对课件不一致。')
    version = info['document_version']
    api.request('POST', f'/api/rag/documents/{document}/grants',
                {'courseId': args.course_id, 'documentVersion': version, 'readExcerpt': True, 'downloadAllowed': False})
    graph = api.request('POST', '/api/rag/graphs', {'courseId': args.course_id, 'chapterId': args.chapter_id,
                        'documentId': document, 'documentVersion': version, 'pageStart': 1, 'pageEnd': 36,
                        'title': '第2章 · 机器学习概述'})
    graph_id = graph['graphId']
    chunks = api.request('GET', f'/api/rag/graphs/{graph_id}/sources')['chunks']
    snapshot = build_snapshot(chunks)
    snapshot['revision'] = graph['revision']
    api.request('PATCH', f'/api/rag/graphs/{graph_id}/draft', snapshot)
    print(json.dumps({'graphId': graph_id, 'documentId': document, 'nodes': len(snapshot['nodes']),
                      'edges': len(snapshot['edges']), 'status': 'REVIEW_REQUIRED',
                      'url': f'http://127.0.0.1:5173/chapter-knowledge-graph?courseId={args.course_id}&graphId={graph_id}'},
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
