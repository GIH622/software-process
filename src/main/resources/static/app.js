/*
 * 前端仅做表单交互；真正的权限、逾期、限额与事务全部由后端再次校验。
 * 登录口令仅用于本次 HTTPS/localhost 请求，不写 localStorage / cookie。
 * 所有后端返回数据使用 textContent 显示，避免把书名等数据库字段作为 HTML 执行。
 */
let csrfToken = '';
let currentRole = '';
const $ = id => document.getElementById(id);
const message = (text, error = false) => {
  $('message').textContent = text;
  $('message').style.borderLeftColor = error ? '#b45351' : '#1b796d';
};
async function api(path, method = 'GET', data) {
  const headers = {};
  if (data !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET' && path !== '/api/auth/login') headers['X-CSRF-Token'] = csrfToken;
  const res = await fetch(path, {method, headers, credentials:'same-origin', body:data === undefined ? undefined : JSON.stringify(data)});
  const body = await res.json().catch(() => ({message:'服务器没有返回有效 JSON'}));
  if (!res.ok) throw new Error(`${res.status} · ${body.message || '请求失败'}`);
  return body.data;
}
async function run(form, handler) {
  const btn = form.querySelector('button[type=submit],button:not([type])');
  if(btn) btn.disabled = true;
  try { await handler(); } catch(e) { message(e.message, true); }
  finally { if(btn) btn.disabled = false; }
}
function renderTable(container, rows) {
  container.replaceChildren();
  if(!Array.isArray(rows) || rows.length === 0){container.textContent = '暂无记录';return;}
  const columns=Object.keys(rows[0]);const table=document.createElement('table');
  const head=document.createElement('thead');const hr=document.createElement('tr');
  columns.forEach(name=>{const th=document.createElement('th');th.textContent=name;hr.append(th);});head.append(hr);table.append(head);
  const tbody=document.createElement('tbody');
  rows.forEach(row=>{const tr=document.createElement('tr');columns.forEach(k=>{
    const td=document.createElement('td');td.textContent=row[k] == null ? '-' : String(row[k]);tr.append(td);
  });tbody.append(tr);});table.append(tbody);container.append(table);
}
function showLoggedIn(data){
  csrfToken=data.csrfToken;currentRole=data.role;
  $('loginPanel').hidden=true;$('work').hidden=false;
  $('who').textContent=`${data.role==='ADMIN'?'管理员':'读者'} · ${data.name}`;
  $('session').textContent='已登录';
  $('myPanel').hidden=data.role!=='READER';
  $('adminPanel').hidden=data.role!=='ADMIN';
  if(data.role==='READER'){$('borrowReader').value=data.account; $('borrowReader').readOnly=true;}
  else {$('borrowReader').value='';$('borrowReader').readOnly=false;}
}
$('loginForm').addEventListener('submit',event=>{event.preventDefault();run(event.currentTarget,async()=>{
  const account=$('loginAccount').value.trim();const password=$('loginPassword').value;
  const data=await api('/api/auth/login','POST',{account,password});
  $('loginPassword').value='';showLoggedIn(data);message('登录成功，可以进行业务操作。');
  await searchBooks();if(currentRole==='READER')await loadMine();
});});
$('logout').addEventListener('click',async()=>{try {await api('/api/auth/logout','POST');location.reload();}catch(e){message(e.message,true);}});
async function searchBooks(){renderTable($('bookResults'),await api('/api/books?keyword='+encodeURIComponent($('keyword').value.trim())));}
$('searchForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,searchBooks);});
$('borrowForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,async()=>{
  const res=await api('/api/borrow','POST',{readerCardNo:$('borrowReader').value.trim(),barcode:$('borrowBarcode').value.trim()});
  message(`借阅成功：记录 ${res.recordId}，应还日 ${res.dueDate}`);await searchBooks();if(currentRole==='READER')await loadMine();
});});
$('returnForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,async()=>{
  const res=await api('/api/return','POST',{barcode:$('returnBarcode').value.trim()});
  message(`归还成功：${res.bookTitle}，逾期 ${res.overdueDays} 天，读者状态 ${res.readerStatus}`);
  await searchBooks();if(currentRole==='READER')await loadMine();
});});
async function loadMine(){renderTable($('myLoans'),await api('/api/borrow_records/my'));}
$('myRefresh').addEventListener('click',()=>loadMine().catch(e=>message(e.message,true)));
function fields(form){return Object.fromEntries(new FormData(form).entries());}
$('addBookForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,async()=>{
  const result=await api('/api/admin/books','POST',fields(e.currentTarget));
  message(`新增图书成功，主键 ${result.bookId}`);e.currentTarget.reset();await searchBooks();
});});
$('addReaderForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,async()=>{
  const form=fields(e.currentTarget);form.borrowLimit=Number(form.borrowLimit);
  const result=await api('/api/admin/readers','POST',form);
  message(`新增读者成功，主键 ${result.readerId}`);e.currentTarget.reset();
});});
$('overdueBtn').addEventListener('click',async()=>{try{renderTable($('adminResults'),await api('/api/admin/overdue'));}catch(e){message(e.message,true);}});
$('logsBtn').addEventListener('click',async()=>{try{renderTable($('adminResults'),await api('/api/admin/logs'));}catch(e){message(e.message,true);}});
const actionButton=(label,handler,disabled=false)=>{const b=document.createElement('button');
  b.type='button';b.className='secondary';b.textContent=label;b.disabled=disabled;
  b.addEventListener('click',handler);return b;};
function renderReaderTable(container,rows){
  container.replaceChildren();
  if(!Array.isArray(rows)||rows.length===0){container.textContent='暂无读者';return;}
  const table=document.createElement('table');
  const head=document.createElement('thead');const hr=document.createElement('tr');
  ['证号','姓名','状态','已借/上限','操作'].forEach(name=>{const th=document.createElement('th');th.textContent=name;hr.append(th);});
  head.append(hr);table.append(head);
  const tbody=document.createElement('tbody');
  rows.forEach(row=>{
    const tr=document.createElement('tr');
    [row.cardNo,row.name,row.status,`${row.borrowedCount}/${row.borrowLimit}`].forEach(value=>{
      const td=document.createElement('td');td.textContent=value==null?'-':String(value);tr.append(td);});
    const td=document.createElement('td');
    if(row.status==='INACTIVE'){td.textContent='已注销';}
    else{
      td.append(actionButton('冻结',()=>readerAction(row.readerId,'freeze'),row.status==='FROZEN'));
      td.append(actionButton('解冻',()=>readerAction(row.readerId,'unfreeze'),row.status!=='FROZEN'));
      td.append(actionButton('注销',()=>readerAction(row.readerId,'deactivate'),Number(row.borrowedCount)>0));
    }
    tr.append(td);tbody.append(tr);
  });
  table.append(tbody);container.append(table);
}
async function loadReaders(){
  try{renderReaderTable($('readerAdminResults'),await api('/api/admin/readers'));}
  catch(e){message(e.message,true);}
}
async function readerAction(id,action){
  try{
    const status=await api(`/api/admin/readers/${id}/${action}`,'POST');
    message(action==='deactivate'?`读者 ${id} 已注销`:`读者 ${id} 操作成功，当前状态 ${status}`);
    await loadReaders();
  }catch(e){message(e.message,true);}
}
$('readerListBtn').addEventListener('click',loadReaders);
$('retireBookForm').addEventListener('submit',e=>{e.preventDefault();run(e.currentTarget,async()=>{
  const barcode=fields(e.currentTarget).barcode.trim();
  await api('/api/admin/books/'+encodeURIComponent(barcode)+'/retire','POST');
  message(`已下架 ${barcode}`);e.currentTarget.reset();await searchBooks();
});});
// 浏览器刷新后，恢复仍有效的服务器会话（不在 localStorage 保存口令或 CSRF）。
api('/api/auth/me').then(data=>{showLoggedIn(data);searchBooks().catch(()=>{});if(currentRole==='READER')loadMine().catch(()=>{});}).catch(()=>{});
