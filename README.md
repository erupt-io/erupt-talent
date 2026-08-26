# erupt-hr · 全流程招聘管理系统

基于 [Erupt](https://github.com/erupts/erupt) 低代码框架实现的企业社招全流程管理系统，覆盖
**简历 → 初筛 → 多轮面试 → 拟录用 → Offer 审批与自动发件 → 候选人确认 → 待入职 → 正式入职 → 数据漏斗** 全链路。

界面由注解生成，无前端代码；邮箱抓取、Offer 发信、钉钉日程三项外部集成全部在后台**表单中可视化配置**，
不需要改 `application.yml`，也不需要重启。

---

## 快速开始

```bash
mvn spring-boot:run
```

打开 <http://localhost:8080>，默认账号 `erupt` / `erupt`（首次登录建议立即改密）。

首次启动会自动完成初始化（幂等，可重复启动）：

- 建表（H2 文件库，落在 `./data/`）
- 播种「招聘管理」菜单树及每个模型的按钮级权限
- 播种角色 `TALENT_HR`（HR专员）、`TALENT_INTERVIEWER`（面试官）并绑定菜单
- 播种 6 个通知场景、1 条系统配置、2 个定时任务

端口被占用时可以换端口启动：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8099
```

### 切换到 MySQL

```bash
export MYSQL_USER=... MYSQL_PASSWORD=...
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=mysql
```

数据库凭据只从环境变量读取，不写进配置文件。

---

## 菜单与模型

| 菜单 | 类型 | 实现类 | 说明 |
|------|------|--------|------|
| 招聘数据看板 | TPL 页面 | `tpl/TalentDashboardTpl` + `controller/TalentDashboardController` | 漏斗图、核心指标、渠道/岗位分布、CSV 导出 |
| 岗位管理 | 表格 | `model/JobPost` | 岗位、HC、启停、部门负责人（Offer 首级审批人） |
| 简历管理 | 表格 | `model/Resume` | 主数据；支持手动录入、Excel 导入、邮箱自动抓取 |
| 面试管理 | 表格 | `model/Interview` | 一/二/终面记录、打分评价、钉钉日程 |
| Offer管理 | 表格 | `model/Offer` | 两级审批 + 自动发件 + 全程留痕 |
| 入职管理 | 表格 | `model/Onboarding` | 材料核对、确认入职弹窗、闭环 |
| 流转日志 | 表格（只读） | `model/TalentTrace` | 所有状态变更永久留存 |
| 招聘系统配置 | 表单视图 | `model/TalentSetting` | IMAP / SMTP / 钉钉 / 风控开关 / Offer 模板 |

---

## 可视化配置（招聘系统配置）

仅超级管理员可见可改（`proxy/AdminOnlyPower`）。四个分组：

| 分组 | 内容 |
|------|------|
| 邮箱抓取简历 | 开关、IMAP 服务器/端口/SSL、账号、密码、文件夹、回溯天数、主题关键字过滤，**测试邮箱连接**按钮 |
| Offer 邮件发送 | 开关、SMTP 服务器/端口/SSL、账号、密码、发件人显示名，**测试发信**按钮 |
| 钉钉日程同步 | 开关、AppKey、AppSecret，**测试钉钉凭据**按钮 |
| Offer 模板与风控 | 全自动发放开关、公司名称、默认报到地点、邮件主题、邮件模板（代码编辑器） |

### 凭据处理

- 三个密钥字段使用 `EditType.PASSWORD`。框架只向浏览器下发掩码 `••••••`，**明文永不回传**。
- 表单视图的保存不会自动还原掩码，因此 `proxy/TalentSettingProxy#formSave` 显式处理：
  提交值是掩码或留空时保留库中原值，避免误清空凭据。
- 三个"测试"按钮拿到的是浏览器提交的表单对象，同样先把掩码解析回库中真实值再发起连接。
- 凭据不写日志、不进 Excel 导出、不出现在列表页。

### Offer 邮件模板变量

`${candidateName}` `${gender}` `${jobPost}` `${salary}` `${probationMonths}`
`${onboardDate}` `${onboardDateCn}` `${expireTime}` `${offerCode}` `${companyName}` `${reportAddress}`

变量值在替换时做 HTML 转义——候选人姓名、岗位等可能来自邮件解析，属于不可信输入。

---

## 自动化规则实现对照

| # | 需求文档规则 | 实现位置 |
|---|-------------|---------|
| 1 | 定时抓取招聘邮箱投递简历，自动解析录入 | `job/MailFetchJob` → `service/MailFetchService` + `service/ResumeParser` |
| 2 | 面试信息提交完成，自动创建钉钉日程并推送提醒 | `service/InterviewFlowService#syncCalendar` → `service/DingTalkService` |
| 3 | 初筛通过 → 自动创建一面任务、推送待办 | `service/ResumeFlowService#screenPass` |
| 4 | 多轮面试结果提交 → 自动同步更新简历状态 | `service/InterviewFlowService#onConclusion` |
| 5 | 终面拟录用 → 自动发起 Offer 审批 | `service/OfferFlowService#createFromProposal` |
| 6 | Offer 审批完成 → 自动发送邮件、更新双向状态 | `service/OfferFlowService#hrApprove` / `#send` |
| 7 | Offer 确认接受 → 自动生成待入职数据 | `service/OnboardFlowService#createFromOffer` |
| 8 | 点击入职登记，触发弹窗消息提醒 | `handler/OnboardConfirmHandler#eruptFormValue` + `model/form/OnboardConfirmForm` |
| 9 | 完成正式入职办理 → 闭环全流程 | `service/OnboardFlowService#confirm` |
| 10 | 所有状态变更永久留存操作日志 | `service/TraceService` → `model/TalentTrace` |

### 状态机与防篡改

- 业务状态单向闭环：`Resume.status`、`Offer.status`、`Onboarding.status` 全部
  `@Readonly(allowChange = false)`，接口层无法直接改写，只能经服务层流转。
- 唯一例外是简历的「状态回溯修正」行操作，`@ExprBool(exprHandler = AdminOnly.class)` 限超管可见，
  服务层再次校验，且必须填写原因，修正本身也进流转日志。
- 编号规则由 `service/SeqService` 保证唯一：按 `前缀 + yyyyMMdd` 行级悲观锁自增，
  在独立事务中消费号段（业务回滚只会跳号，不会重号）。

### 容错设计

- **钉钉同步失败不阻断面试记录**：异常写入 `Interview.dingSyncError` 与流转日志，HR 可见可重试。
- **发件失败不回滚审批**：`OfferFlowService#send` 不抛异常，Offer 停在「待发送」并记录原因，
  提供「重新发送」按钮。审批结论一旦做出就不会因为邮件服务器不可用而丢失。
- **通知失败不影响业务**：`NotifyService` 独立事务 + 异常吞掉，且为定时任务场景构造系统用户上下文。

---

## 权限体系（需求 4.7）

| 角色 | 实现 | 效果 |
|------|------|------|
| 超级管理员 | `isAdmin` | 全功能、全数据 + 系统配置 |
| HR专员 `TALENT_HR` | `proxy/HrOnlyPower` | 全流程操作、数据查看导出；**无系统配置权限** |
| 面试官 `TALENT_INTERVIEWER` | `proxy/InterviewPower` + `proxy/OwnInterviewFilter` + `proxy/HrOnlyReadonly` | 只看得到自己的面试；只能填打分/评价/结论；排期字段（面试官、时间、形式、地点、时长）提交也会被忽略 |
| 普通员工 | 未授予角色 | 无任何招聘菜单与数据 |

---

## 验收标准核对

以下均已在本机实跑验证（H2 + 本地 SMTP 接收服务）：

| # | 验收项 | 结果 |
|---|--------|------|
| 1 | 字段、状态、流程与文档一致 | ✅ 简历 14 态、Offer 8 态、入职 3 态全链路走通 |
| 2 | 邮箱简历自动抓取、解析、录入 | ⚠️ 见下方"未端到端验证" |
| 3 | 面试同步钉钉日程 | ⚠️ 见下方"未端到端验证" |
| 4 | 入职弹窗提醒，信息完整，防误触 | ✅ 弹窗预填候选人/岗位/计划日期/待核对材料清单；未勾选确认则拒绝；重复确认被拒 |
| 5 | 自动化流程触发准确、数据同步无误 | ✅ 规则 3/4/5/6/7/8/9 逐条验证 |
| 6 | Offer 邮件自动发送、变量替换正常 | ✅ 真实 SMTP 收到邮件，变量全部替换，含中文入职日期与报到地点 |
| 7 | 数据统计、漏斗图表精准 | ✅ 8 级漏斗 + 环节/整体转化率 + 各轮通过率 + 渠道/岗位分布 + 按时间/岗位/渠道筛选 + CSV 导出 |
| 8 | 角色权限严格生效 | ✅ 面试官越权访问简历返回 403，看板被拒，排期字段篡改被忽略 |
| 9 | 全流程操作日志完整可查 | ✅ 每次状态变更留存操作人、时间、前后状态、详情 |

### 未端到端验证的部分

两项外部集成需要真实凭据，本机无法闭环，代码按官方接口规范实现，失败路径已验证：

- **IMAP 邮箱抓取**：`MailFetchService` 的连接、MIME 递归解析、PDF/DOCX 正文提取、
  按 `Message-ID` 唯一约束去重、附件按 erupt 上传目录规则落盘均已实现；
  纯解析逻辑由 `ResumeParserTest`（6 个用例）覆盖；未配置邮箱时定时任务优雅跳过已验证。
  **需要配置一个真实招聘邮箱后回归验证抓取与解析结果。**
- **钉钉日程**：`DingTalkService` 按钉钉开放平台实现双 token（`oapi` 通讯录 + `v1.0` 日程），
  手机号 → userId 解析、日程创建与提醒；未配置时记录 `dingSyncError` 已验证。
  **需要钉钉企业应用的 AppKey/AppSecret 及「日程」「通讯录读取」权限后回归验证。**

面试官与候选人的对应关系依赖 `EruptUser.phone`，请确保面试官账号维护了与钉钉一致的手机号。

---

## 测试

```bash
mvn test
```

---

## 依赖说明

- Erupt `2.1.0`（`erupt-spring-boot-starter` + `erupt-job` + `erupt-notice`），Java 17，Spring Boot 3.5.x
- `freemarker`：看板 TPL 页面的模板引擎（erupt-tpl 中声明为 optional）
- `pdfbox` / POI：简历附件正文提取
- Erupt 2.1.0 若未发布到中央仓库，需先在 erupt 源码仓库执行：
  `mvn install -DskipTests -pl erupt-starter/erupt-spring-boot-starter,erupt-plugin/erupt-job,erupt-plugin/erupt-notice -am`

### 开发期两个坑（已在代码注释中标注）

- `@Dynamic(condition = ...)` 的 JS 表达式**只有一个变量 `value`**（依赖字段的值），
  前后端一致；注解 javadoc 写的"变量为各字段名"与实现不符。写成字段名会在保存时抛 `ReferenceError`。
- `spring.jpa.properties.hibernate.globally_quoted_identifiers=true` 会把
  `@Column(columnDefinition = "text")` 也加上引号，变成 H2 不认识的域名；大文本字段只用 `@Lob`。
