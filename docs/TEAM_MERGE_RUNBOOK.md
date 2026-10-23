# 四人团队实际 GitHub 仓库：安全整合与分批提交步骤

## A. 不要直接覆盖本地克隆
本次 `software-process.zip` 自带 `.git/`、HEAD=`feature/login-page`，
而快照中 `main` 落后于 `origin/main`；另有四个工作区文件标记为 M。
这是**快照时刻**，你当前电脑可能已经更新。先在你电脑打开真正的克隆文件夹执行：

```powershell
git status --short --branch
git branch -vv
git remote -v
git log -1 --oneline
```

**先处理未提交修改**：逐一确认是否队友修改；需要保留的由实际作者提交或备份。
在有未提交修改的前提下，不要盲目 `git switch main`、`git restore` 或 `git reset --hard`。

## B. 推荐的增量合入路线
1. 先用 `git fetch origin` 获取最新引用（不覆盖工作区）。
2. 工作区干净之后，在真实克隆中从远端主分支开开发分支：
   `git switch -c feature/dev-integration-v4 origin/main`
   （若已有同名分支，改用新的唯一分支名）。
3. 将**开发增量覆盖包**中的内容复制进当前仓库根目录。绝不解压包含 `.git` 的原 ZIP 到仓库。
4. `git status --short` 检查新增文件；本增量包不包含任何团队已有的12个文件。
5. `git diff --check`（已跟踪文件）以及 `git status`，确保无原始数据库数据和密码。
6. 在 feature 分支完成所有依赖齐全的代码文件，再执行 `mvn clean test`。
   **不要为凑提交次数把 BorrowService/Mapper 依赖拆成无法编译的半成品。**
7. 测试通过且小组确认阈值规则后，按真实工作阶段分别 Commit（保留实际作者）：
   - Commit 1：pom.xml、配置、数据库脚本和核心业务代码；
   - Commit 2：前端/Controller/Postman/自动化测试；
   - Commit 3：开发文档、CI、测试证据指引；
   - CR-001：独立可选变更 PR，批准后单独迁移和开启开关。
   上面是**参考顺序**，实际分批必须保证每次提交内容真实且变更可审查。
8. Push 你的 feature 分支，创建 PR 由组长和 QA 审核。**不要直接 push 到 main**。
9. PR 评论必须写明：需求编号、修改原因、测试命令与真实结果、是否改库/迁移、风险、复测人员。
10. 合并后由组长根据真实评审与变更完成情况打 `baseline-req-v1.0/v1.1`。

## C. 验证不意外覆盖队友文件
本次增量包**不包含**：
`README.md`、`library_db.sql`、四个阶段目录、`src/.gitkeep`、任何 `.git/`。
如果你要覆盖已有相同路径的文件，应先逐个进行 VS Code 文件比较，不能无提示替换。

## D. 关于现有 tracked 的 library_db.sql
`.gitignore` 即使加入 `/library_db.sql`，也**不能取消已经跟踪的文件**。
如果全组决定停止跟踪，可先安全备份，确认相关人员同意后在专门 PR 中运行：
`git rm --cached -- library_db.sql`
这会从后续提交中移除跟踪，但**不会清除 Git 历史中的旧内容**；
若包含实际凭证，联系数据库负责人轮换密码并评估历史清理方案。

## E. 学生团队审查分工
- 成员1：版本主线、PR/Tag、真实评审记录及老师规定格式；
- 成员2：SRS唯一权威版本、35天阈值和AC-7/AC-9、R003审批证据、追踪矩阵；
- 成员3：代码、注释、SQL安全、JUnit、MySQL/Postman联调、Git Commit；
- 成员4：实际执行不少于六个功能/异常测试及并发测试，保存原始截图和缺陷记录。

## F. PR 描述模板
```
需求：R001/R002/R004/R005/R006/R007（具体勾选）
修改原因：
涉及模块：
数据库影响：无 / 只读 / 迁移（需另行审批）
验证：mvn clean test —— 实际结果：
Postman / MySQL / 并发实测 —— 结果及截图：
风险与回滚：
审核人：
```
