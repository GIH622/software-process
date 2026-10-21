#!/usr/bin/env python3
"""真实 MySQL 并发测试（仅对 library_dev 的 demo 数据执行）。
运行前确认 BC-CONC-001 是 AVAILABLE 且不存在在借记录；本脚本不会重置数据库。
十个读者独立登录并同时争抢同一实体副本，理应只有一次成功。
标准库实现，不需要安装 requests 等第三方 Python 包。
"""
import concurrent.futures, http.cookiejar, json, os, urllib.error, urllib.request
BASE=os.getenv('BASE_URL','http://127.0.0.1:8080')
PASSWORD=os.getenv('LIBRARY_DEMO_READER_PASSWORD','')
if not PASSWORD:
    raise SystemExit('请先在本机设置 LIBRARY_DEMO_READER_PASSWORD（演示账号密码），不要上传到 Git。')

def one(card):
    jar=http.cookiejar.CookieJar()
    opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    def call(path,data,token=''):
        body=json.dumps(data).encode('utf-8')
        req=urllib.request.Request(BASE+path,data=body,headers={'Content-Type':'application/json',
                        **({'X-CSRF-Token':token} if token else {})},method='POST')
        try:
            with opener.open(req,timeout=12) as resp:return resp.status,json.load(resp)
        except urllib.error.HTTPError as error:
            try: obj=json.load(error)
            except Exception: obj={'message':str(error)}
            return error.code,obj
    status,info=call('/api/auth/login',{'account':str(card),'password':PASSWORD})
    if status!=200:return (card,status,'LOGIN_FAILED')
    token=info['data']['csrfToken']
    status,result=call('/api/borrow',{'readerCardNo':str(card),'barcode':'BC-CONC-001'},token)
    return (card,status,result.get('message',''))

with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:
    results=list(ex.map(one,range(1001,1011)))
for row in results: print(row)
success=sum(r[1]==200 for r in results)
print(f'并发结果：success={success}, expected=1')
if success!=1 or any(r[1] not in (200,409) for r in results):
    raise SystemExit('测试未达到预期；检查数据库初始状态、行锁、HTTP状态与日志。')
print('HTTP 并发条件通过；请继续使用 sql/04_verify_consistency.sql 人工检查真实 MySQL 数据。')
