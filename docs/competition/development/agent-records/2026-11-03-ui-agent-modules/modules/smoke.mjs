import fs from 'node:fs';
import path from 'node:path';
const out = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const base = 'http://127.0.0.1:8080';
const previous = fs.existsSync(path.join(out,'http-evidence.json')) ? JSON.parse(fs.readFileSync(path.join(out,'http-evidence.json'),'utf8')) : null;
const prefix = previous?.ids?.prefix ?? 'other-20261103-' + Date.now();
const tokens = {};
const events = previous?.events ?? [];
const ids = previous?.ids ?? { prefix };
const ok = b => b?.success === true || String(b?.code) === '200';
const redact = value => JSON.parse(JSON.stringify(value, (k,v) => /password|token|email|phone/i.test(k) ? '[REDACTED]' : v));
function flush() {
  fs.writeFileSync(path.join(out, 'http-evidence.json'), JSON.stringify(redact({time: new Date().toISOString(), ids, events}), null, 2));
}
async function req(name, role, method, url, body) {
  const start = Date.now();
  const res = await fetch(base + url, { method, headers: {'Content-Type':'application/json', ...(tokens[role] ? {Authorization:`Bearer ${tokens[role]}`} : {})}, ...(body !== undefined ? {body: JSON.stringify(body)} : {}) });
  let b; const text = await res.text(); try { b = JSON.parse(text); } catch { b = text; }
  if (url === '/auth/login') {
    if (b.data?.token) tokens[role] = b.data.token;
    b = {...b, data: b.data ? {...b.data, token:'[REDACTED]'} : null};
  }
  events.push({name, role, method, url, body: url === '/auth/login' ? '[DEMO LOGIN OMITTED]' : body, http:res.status, response:b, ms:Date.now()-start});
  flush();
  console.log(name, res.status, typeof b === 'object' ? (b.code ?? b.success) : 'text');
  return b;
}
function check(name, pass, details) { events.push({check:name, result:pass?'PASS':'FAIL', details}); flush(); console.log(name, pass?'PASS':'FAIL'); }
async function main() {
  for (const role of ['teacher','student','admin']) {
    const b=await req('login '+role,role,'POST','/auth/login',{username:role,password:'123456'});
    if(!ok(b)) throw Error('login failed '+role);
    ids[role]=b.data.id ?? b.data.userId;
  }
  if(process.argv.includes('--permissions-only')) {await permissionFollowup();return;}
  if(process.argv.includes('--finish-only')) {await finish();return;}
  if(ids.examDone) { await modules(); return; }
  const cats=await req('categories','teacher','GET','/categories');
  const categoryId=cats.data[0].id;
  if(!ids.course) await req('create course','teacher','POST','/courses',{title:prefix,categoryId,status:1,description:'Synthetic API smoke only'});
  const courses=await req('find own course','teacher','GET',`/courses?title=${prefix}`);
  ids.course=courses.data.records[0].id;
  if(!ids.class) await req('create class','teacher','POST','/teaching-classes',{className:prefix,courseId:ids.course,teacherId:ids.teacher,status:1});
  const classes=await req('find own class','teacher','GET',`/teaching-classes?className=${prefix}`);
  ids.class=classes.data.records[0].id;
  if(!ids.memberAdded) { await req('add demo student to own class','teacher','POST','/class-students',{classId:ids.class,studentId:ids.student,status:1}); ids.memberAdded=true; }
  const mkq=(type,content,answers,options)=>({courseId:ids.course,content:prefix+' '+content,type,difficulty:'EASY',answers:answers.map((a,i)=>({answerContent:a,sortOrder:i+1})),options,status:'ACTIVE'});
  const paper={courseId:ids.course,name:prefix,durationMinutes:1,status:'PUBLISHED',ignoreWarnings:true,questions:[
    {inlineQuestion:mkq('SINGLE_CHOICE','2+2?',['A'],[{optionKey:'A',optionContent:'4',sortOrder:1},{optionKey:'B',optionContent:'5',sortOrder:2}]),sortOrder:1,score:60,requiredAnswer:true},
    {inlineQuestion:mkq('SHORT_ANSWER','Explain addition',['Combining numbers']),sortOrder:2,score:40,requiredAnswer:true}
  ]};
  const p=await req('create independent exam paper','teacher','POST','/api/papers',paper);
  if(!ok(p)) throw Error('paper create failed');
  ids.paper=p.data.id;
  const local = d => new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,19);
  const start=new Date(Date.now()+15000);
  const exam={classId:ids.class,paperId:ids.paper,name:prefix,startTime:local(start),endTime:local(new Date(start.getTime()+60000)),durationMinutes:1,status:'DRAFT',maxScreenSwitches:3,disableClipboard:false,publishNotice:false};
  const e=await req('create independent exam','teacher','POST','/api/exams',exam);
  if(!ok(e)) throw Error('exam create failed');
  ids.exam=e.data.id;
  check('exam configured time interval', new Date(e.data.endTime)-new Date(e.data.startTime)===60000,e.data);
  await req('publish exam','teacher','POST',`/api/exams/${ids.exam}/publish`);
  const early=await req('deny early start','student','POST',`/api/exams/${ids.exam}/submissions/start?candidateId=student`);
  check('early start rejected',!ok(early),early);
  const wait=Math.max(0,start-Date.now()+1000);
  if(wait) await new Promise(r=>setTimeout(r,wait));
  const s=await req('start open exam','student','POST',`/api/exams/${ids.exam}/submissions/start?candidateId=student`);
  if(!ok(s)) throw Error('start failed');
  ids.submission=s.data.id;
  const again=await req('duplicate start','student','POST',`/api/exams/${ids.exam}/submissions/start?candidateId=student`);
  check('duplicate start same record',ok(again)&&again.data.id===ids.submission,again.data);
  const snapshot=await req('student submission','student','GET',`/api/submissions/${ids.submission}`);
  const questions=snapshot.data.answers;
  const body={answers:questions.map(q=>({paperQuestionId:q.paperQuestionId,...(q.questionType==='SINGLE_CHOICE'?{selectedOptionKeys:['A']}:{answerContent:'Combining two amounts'})}))};
  const saved=await req('save exam answers','student','POST',`/api/submissions/${ids.submission}/answers`,body);
  check('saved two answers',ok(saved)&&saved.data.answers.length===2,saved.data);
  const submitted=await req('submit exam','student','POST',`/api/submissions/${ids.submission}/submit`);
  check('objective scoring and pending review',ok(submitted)&&submitted.data.objectiveScore===60&&submitted.data.status==='PENDING_MANUAL_REVIEW',submitted.data);
  const duplicate=await req('repeat submit','student','POST',`/api/submissions/${ids.submission}/submit`);
  const list=await req('teacher exam submissions','teacher','GET',`/api/submissions?examId=${ids.exam}`);
  check('repeat submit adds no record',ok(list)&&list.data.length===1,{duplicate,recordCount:list.data?.length});
  const answer=submitted.data.answers.find(q=>q.questionType==='SHORT_ANSWER');
  const graded=await req('grade subjective','teacher','POST',`/api/submissions/${ids.submission}/grade-subjective`,{grades:[{submissionAnswerId:answer.id,score:35,comment:prefix+' good'}]});
  check('grading total95',ok(graded)&&graded.data.totalScore===95&&graded.data.status==='GRADED',graded.data);
  const reread=await req('student result refresh','student','GET',`/api/submissions/${ids.submission}/student`);
  check('student persisted95',ok(reread)&&reread.data.totalScore===95,reread.data);
  const reg=await req('register outsider','anonymous','POST','/auth/register',{username:prefix+'-outsider',password:'Test123456',realName:'Synthetic outsider',roleCode:'student'});
  check('register synthetic outsider',ok(reg),reg);
  const outsider=await req('login outsider','outsider','POST','/auth/login',{username:prefix+'-outsider',password:'Test123456'});
  ids.outsider=outsider.data.id??outsider.data.userId;
  const denied=await req('outsider cannot read submission','outsider','GET',`/api/submissions/${ids.submission}/student`);
  check('submission unauthorized denied',!ok(denied),denied);
  const noStart=await req('outsider cannot start exam','outsider','POST',`/api/exams/${ids.exam}/submissions/start?candidateId=${prefix}`);
  check('exam membership enforced',!ok(noStart),noStart);
  ids.examDone=true;
  flush();
  await modules();
}
async function modules() {
  const outsider=await req('login synthetic outsider phase2','outsider','POST','/auth/login',{username:prefix+'-outsider',password:'Test123456'});
  const notice={classId:ids.class,title:prefix+' notice',content:'Synthetic class notification',noticeType:'teaching',status:1};
  if(!ids.noticeDone) {
    await req('create notice','teacher','POST','/notices',notice);
    const found=await req('find own notice','teacher','GET',`/notices?title=${prefix}`);
    ids.notice=found.data.records[0].id;
    const n=await req('member reads notice','student','GET',`/notices/${ids.notice}`);
    check('notice target student sees content',ok(n)&&n.data.content===notice.content,n.data);
    const hidden=await req('outsider notice read denied','outsider','GET',`/notices/${ids.notice}`);
    check('notice target membership',!ok(hidden),hidden);
    const write=await req('student notice write denied','student','PUT',`/notices/${ids.notice}`,{...notice,content:'Unauthorized change'});
    check('student cannot modify notice',!ok(write),write);
    await req('teacher updates notice','teacher','PUT',`/notices/${ids.notice}`,{...notice,content:'Synthetic updated'});
    const reread=await req('updated notice refresh','student','GET',`/notices/${ids.notice}`);
    check('notice update persisted',ok(reread)&&reread.data.content==='Synthetic updated',reread.data);
    await req('delete own test notice','teacher','DELETE',`/notices/${ids.notice}`);
    const removed=await req('deleted notice missing','student','GET',`/notices/${ids.notice}`);
    check('notice deletion persisted',!ok(removed)&&String(removed.code)==='404',removed);
    ids.noticeDone=true;flush();
  }
  if(!ids.lesson) {
    await req('create synthetic chapter','teacher','POST','/chapters',{courseId:ids.course,title:prefix,sortOrder:1});
    const c=await req('find chapter','teacher','GET',`/chapters?courseId=${ids.course}`);
    ids.chapter=c.data.records[0].id;
    await req('create synthetic lesson','teacher','POST','/lessons',{chapterId:ids.chapter,title:prefix,resourceType:'text',content:'Synthetic addition lesson',status:1,sortOrder:1});
    const l=await req('find lesson','teacher','GET',`/lessons?chapterId=${ids.chapter}`);
    ids.lesson=l.data.records[0].id;flush();
  }
  if(!ids.learningDone) {
    const progress={classId:ids.class,lessonId:ids.lesson,studentId:ids.student,progress:45,learnedSeconds:30,lastPosition:30};
    await req('save learning progress','student','POST','/learning-progress',progress);
    const p=await req('refresh progress list','student','GET',`/learning-progress?lessonId=${ids.lesson}`);
    ids.progress=p.data.records[0].id;
    check('progress45 persisted',p.data.records[0].progress===45,p.data.records[0]);
    const pr=await req('outsider progress read','outsider','GET',`/learning-progress/${ids.progress}`);
    check('progress cross identity denied',!ok(pr),pr);
    const pw=await req('outsider progress write','outsider','PUT',`/learning-progress/${ids.progress}`,{...progress,progress:100});
    check('progress cross identity write denied',!ok(pw),pw);
    await req('progress valid update','student','PUT',`/learning-progress/${ids.progress}`,{...progress,progress:70});
    const invalid=await req('progress101 rejected','student','PUT',`/learning-progress/${ids.progress}`,{...progress,progress:101});
    check('progress upper boundary',!ok(invalid),invalid);
    const pu=await req('progress update refresh','student','GET',`/learning-progress/${ids.progress}`);
    check('progress70 persisted',ok(pu)&&pu.data.progress===70,pu.data);
    const note={title:prefix,contentMd:'# Synthetic note\nAddition combines quantities.',lastVideoTime:12};
    const saved=await req('save personal note','student','PUT',`/lessons/${ids.lesson}/note`,note);
    const nr=await req('refresh personal note','student','GET',`/lessons/${ids.lesson}/note`);
    check('note persisted content',ok(nr)&&nr.data.contentMd===note.contentMd,nr.data);
    const forbidden=await req('outsider cannot read lesson note','outsider','GET',`/lessons/${ids.lesson}/note`);
    check('note outsider lesson denied',!ok(forbidden),forbidden);
    ids.learningDone=true;flush();
  }
  if(!ids.adminDone) {
    const cat={name:prefix,description:'Synthetic category',sortOrder:99};
    const rejected=await req('student cannot create category','student','POST','/categories',cat);
    check('category admin only',!ok(rejected),rejected);
    await req('admin creates category','admin','POST','/categories',cat);
    const cats=await req('find synthetic category','admin','GET','/categories');
    ids.category=cats.data.find(x=>x.name===prefix)?.id;
    check('admin category persisted',!!ids.category,{id:ids.category});
    await req('admin category update','admin','PUT',`/categories/${ids.category}`,{...cat,name:prefix+' updated'});
    await req('admin removes unused category','admin','DELETE',`/categories/${ids.category}`);
    const users=await req('student forbidden user administration','student','GET','/users');
    check('student user listing denied',!ok(users),{code:users.code,msg:users.msg});
    const user={username:prefix+'-admin-created',password:'Test123456',nickname:'Synthetic managed user',roleCode:'student',status:1};
    const noCreate=await req('teacher cannot create user','teacher','POST','/users',user);
    check('teacher user create denied',!ok(noCreate),noCreate);
    const uc=await req('admin creates synthetic user','admin','POST','/users',user);
    check('admin user create',ok(uc),uc);
    const uf=await req('find synthetic managed user','admin','GET',`/users?username=${user.username}`);
    ids.managedUser=uf.data.records?.[0]?.id;
    const app=await req('synthetic teacher application','anonymous','POST','/teacher-applications',{username:prefix+'-applicant',password:'Test123456',nickname:'Synthetic applicant',email:'synthetic@example.invalid',reason:'Synthetic permission smoke'});
    if(ok(app)) {
      ids.application=app.data.id;
      const review=await req('student application review denied','student','POST',`/teacher-applications/${ids.application}/review`,{status:2,reviewComment:'Unauthorized'});
      check('application review requires admin',!ok(review),review);
      const ar=await req('admin rejects synthetic application','admin','POST',`/teacher-applications/${ids.application}/review`,{status:2,reviewComment:prefix+' synthetic reject'});
      check('admin application review persisted',ok(ar)&&ar.data.status===2,ar.data);
    }
    ids.adminDone=true;flush();
  }
  if(!ids.externalDone) {
    const badVideo=await req('video rejects non HTTP URL','teacher','POST','/video-resources/url',{lessonId:ids.lesson,url:'file:///synthetic.mp4'});
    check('video scheme guard',!ok(badVideo),badVideo);
    const playback=await req('text lesson playback metadata','student','GET',`/lessons/${ids.lesson}/playback`);
    check('playback reports explicit no video',ok(playback)&&!playback.data.playUrl,playback.data);
    const paper=await req('single external arxiv search','teacher','GET','/api/paper/search?keyword=machine%20learning&source=arxiv&page=1&size=1');
    check('paper search business response',ok(paper)&&!!paper.data,paper);
    const cfg={title:prefix,categoryId:1,audience:'Beginning students',totalHours:1,chapterCount:1,lessonsPerChapter:1,teachingGoals:'Explain addition of two positive integers',extraInstructions:'Create only one chapter and one short lesson.'};
    const prep=await req('create preparation','teacher','POST','/api/preparations',cfg);
    if(ok(prep)) {
      ids.preparation=prep.data.id;
      const denied=await req('student cannot read preparation','student','GET',`/api/preparations/${ids.preparation}`);
      check('preparation student denied',!ok(denied),denied);
      const gen=await req('single generate preparation outline','teacher','POST',`/api/preparations/${ids.preparation}/generate-outline`);
      ids.generationRequested=true;flush();
      for(let i=0;i<18;i++) {
        const p=await req('poll preparation '+i,'teacher','GET',`/api/preparations/${ids.preparation}`);
        if(p.data?.generationStatus!=='RUNNING'&&p.data?.generationStatus!=='PENDING') {
          check('preparation outline generated',ok(p)&&p.data?.outline?.chapters?.length===1,{generationStatus:p.data?.generationStatus,errorMessage:p.data?.errorMessage,outline:p.data?.outline});
          break;
        }
        await new Promise(r=>setTimeout(r,5000));
      }
    }
    ids.externalDone=true;flush();
  }
  await finish();
}
async function permissionFollowup() {
  const info=await req('confirm student authenticated role','student','GET','/auth/info');
  check('student credential role',info.data.roleCode==='student',{roleCode:info.data.roleCode,id:info.data.id??info.data.userId});
  const anonymous=await req('anonymous category write denied','anonymous','POST','/categories',{name:prefix+'-anonymous',sortOrder:1});
  check('anonymous category write denied',!ok(anonymous),anonymous);
  const cat={name:prefix+'-permission-repro',sortOrder:1};
  const created=await req('repro student creates category','student','POST','/categories',cat);
  const cats=await req('admin locate repro category','admin','GET','/categories');
  const own=cats.data.filter(c=>c.name===cat.name);
  check('FAIL repro student category creation forbidden',!ok(created)&&own.length===0,{httpBusinessAccepted:ok(created),persistedCount:own.length,ids:own.map(x=>x.id)});
  for(const c of own) {
    const updated=await req('repro student updates own category','student','PUT',`/categories/${c.id}`,{...cat,name:prefix+'-changed'});
    check('FAIL repro student category update forbidden',!ok(updated),{categoryId:c.id,accepted:ok(updated)});
    await req('admin cleans repro category','admin','DELETE',`/categories/${c.id}`);
  }
  for(const c of cats.data.filter(c=>c.name.startsWith(prefix))) {
    if(own.some(o=>o.id===c.id))continue;
    await req('admin cleans leftover synthetic category','admin','DELETE',`/categories/${c.id}`);
  }
  const users=await req('verify student user list scope','student','GET','/users');
  const rows=users.data?.records??[];
  check('student users list contains only self',ok(users)&&rows.length===1&&rows.every(x=>x.id===ids.student),{recordCount:rows.length,onlySelf:rows.every(x=>x.id===ids.student),fields:rows[0]?Object.keys(rows[0]):[],sensitiveFieldPresence:rows[0]?Object.fromEntries(['password','email','phone'].map(k=>[k,{fieldPresent:k in rows[0],nonempty:!!rows[0][k]}])):{}});
  const cross=await req('student cannot read synthetic other user','student','GET',`/users/${ids.managedUser}`);
  check('student other user denied',!ok(cross),cross);
  events.push({correction:'student user listing denied was an incorrect test expectation; expected behavior is own profile only, verified above. Initial FAIL is retained as test error, not product issue.'});
  ids.permissionDone=true;flush();
}
async function finish() {
  if(ids.finishDone)return;
  const ended=await req('ended exam start rejected','student','POST',`/api/exams/${ids.exam}/submissions/start?candidateId=student`);
  check('ended exam cannot start',!ok(ended),ended);
  const result=await req('submit exam results','teacher','POST',`/api/exams/${ids.exam}/results/submit`);
  check('exam results aggregate95',ok(result)&&result.data.averageScore===95&&result.data.studentScores.length===1,result.data);
  for(const format of ['csv','xlsx','txt']) {
    const url=`/api/exams/${ids.exam}/results/export?format=${format}`;
    const res=await fetch(base+url,{headers:{Authorization:`Bearer ${tokens.teacher}`}});
    const bytes=Buffer.from(await res.arrayBuffer());
    if(res.status===200) {
      fs.writeFileSync(path.join(out,`synthetic-exam-score.${format}`),bytes);
      events.push({name:'export exam '+format,http:res.status,contentType:res.headers.get('content-type'),bytes:bytes.length,body:format==='csv'?bytes.toString('utf8'):undefined});
      check('export '+format+' format',format==='csv'?bytes.toString('utf8').includes('95'):format==='xlsx'?bytes.slice(0,2).toString()==='PK':false,{bytes:bytes.length});
    } else {
      let b;try{b=JSON.parse(bytes.toString('utf8'));}catch{b=bytes.toString('utf8');}
      events.push({name:'export exam '+format,http:res.status,response:b});
      check('unsupported export '+format+' rejected',format==='txt'&&res.status===400,b);
    }
    flush();
  }
  const studentExport=await req('student cannot export class results','student','GET',`/api/exams/${ids.exam}/results/export?format=csv`);
  check('exam export teacher only',!ok(studentExport),studentExport);
  ids.finishDone=true;flush();
}
main().catch(e=>{ events.push({error:e.message});flush();console.error(e.message); process.exitCode=1; });
