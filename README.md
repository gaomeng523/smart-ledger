# 萌萌记账 · smart-ledger

一个面向个人的智能记账系统：微信小程序前端 + Spring Boot 后端。支持一句话记多笔、拍照识别小票，AI 自动提取金额、分类与支付渠道，并提供日历账单、分类统计、预算提醒、周期订阅、心愿储蓄等完整闭环。

## 功能

- **AI 快速记账**：输入「午饭 30 微信」即可自动解析金额、收支类型、分类、支付渠道与日期，解析结果可二次编辑；解析失败自动降级为手动表单
- **一句话多笔**：输入「买衣服 3100，车票 398」自动拆成两笔，汇总确认后一次入库
- **拍照识小票**：上传小票或支付截图，视觉模型提取金额、商户、日期与分类，字段可修改后保存
- **检索增强分类**：解析前用向量检索召回相似历史记账作为 few-shot，统一分类口径（见下方评测）
- **智能账单问答**：直接问「这个月吃饭花了多少」「比上月多花多少」，模型把问题翻译成只读 SQL → 查询 → 中文回答（见下方评测）
- **日历账单**：按日聚合收支，支持按类型、分类筛选与本月汇总
- **分类统计**：9 个支出一级分类 + 5 个收入一级分类，共 54 个二级分类，支持按一级汇总并下钻查看二级明细
- **预算管理**：月度预算与分类预算，超支预警
- **周期订阅**：房租、会员费等固定扣费提醒
- **心愿储蓄**：设定目标金额与计划日期，记录存取进度
- **记账模板**：把常记的账存为模板，一键复用
- **资产管理**：手工录入账户余额，本机保存
- **辅助能力**：账单图片上传、CSV 导入导出、消费洞察、分类别名映射

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.7、JJWT 0.12.5、Caffeine、Bean Validation |
| 数据库 | MySQL 8（utf8mb4） |
| AI | DeepSeek Chat（文本解析）、Qwen-VL（拍照识别）、text-embedding-v4（检索增强） |
| 前端 | 微信小程序原生（WXML / WXSS / JavaScript），自定义组件化、深色设计令牌 |
| 部署 | Nginx（HTTPS 反向代理）+ systemd + 腾讯云轻量服务器 |

## 目录结构

```
.
├── docs/                          需求、概要设计、数据库、接口、测试、UI 等文档
└── Accounting system/
    ├── backend/                   Spring Boot 后端
    │   ├── src/main/java/com/smartledger/
    │   │   ├── controller/        15 个 REST 控制器
    │   │   ├── service/           业务逻辑
    │   │   ├── ai/                文本/视觉解析、Prompt 构建、结果校验
    │   │   ├── ai/ask/            智能问答：Prompt、只读 SQL 守卫、参数化执行
    │   │   ├── ai/rag/            向量检索：示例库加载、余弦召回、失败降级
    │   │   ├── ai/eval/           离线评测器（解析三组对照 + 问答评测）
    │   │   ├── mapper/            MyBatis-Plus 数据访问
    │   │   ├── entity/ dto/       实体与传输对象
    │   │   ├── config/            拦截器、缓存、静态资源映射
    │   │   └── common/            统一响应、异常处理、工具类
    │   ├── src/test/              单元测试（SQL 守卫、问答编排、答案比对、统计口径）
    │   ├── tools/eval/            评测集生成脚本与历史报告
    │   └── src/main/resources/
    │       ├── ai/                检索示例库与评测集（JSONL）
    │       ├── db/schema.sql      建表脚本（可重复执行）
    │       ├── db/data.sql        字典数据（分类、支付渠道）
    │       └── application*.yml   多环境配置
    ├── miniprogram/               微信小程序
    │   ├── pages/                 14 个页面（首页、日历、统计、我的、登录等）
    │   ├── components/            自定义组件（账单表单、分类选择器、空态、抽屉等）
    │   ├── utils/                 请求封装、登录鉴权、存储、格式化
    │   └── config/env.js          环境与接口地址配置
    └── deploy/                    部署套件
        ├── nginx/                 站点配置（HTTPS + 反向代理）
        ├── systemd/               服务单元文件
        ├── env/                   环境变量模板
        └── server-setup.sh        服务器初始化脚本
```

## AI 解析与评测

文本解析支持三种链路，通过环境变量切换：

| 模式 | 说明 | 估算成本 |
|---|---|---|
| 本地规则 | 关键词 + 正则，零网络调用，AI 关闭时的默认链路 | 0 |
| 大模型 | DeepSeek 结构化输出 + JSON 校验 + 失败重试 | 约 ¥0.002 / 次 |
| 大模型 + RAG | 解析前用 `text-embedding-v4` 召回 Top-5 相似历史记账作为 few-shot | 约 ¥0.003 / 次 |

在自建评测集上的实测结果（216 条：108 条检索示例 + 108 条测试集，覆盖 54 个二级分类，两组句式刻意不重叠）：

| 链路 | 金额准确率 | 分类 Top-1 | 全字段通过率 | 平均延迟 | P95 |
|---|---|---|---|---|---|
| 本地规则 | 100% | 58.33% | 58.33% | 3 ms | 1 ms |
| 大模型 | 100% | 82.41% | 82.41% | 1232 ms | 2001 ms |
| 大模型 + RAG | 100% | **90.74%** | **90.74%** | 1904 ms | 6351 ms |

复现方式：

```bash
# 1. 重新生成评测集（固定随机种子）
python backend/tools/eval/gen_eval_dataset.py

# 2. 跑三组对照（需数据库与模型 Key）
java -jar target/smart-ledger-backend-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.main.web-application-type=none \
  --smartledger.eval.enabled=true \
  --smartledger.ai.enabled=true \
  --smartledger.ai.rag.enabled=true
```

完整结果见 `backend/tools/eval/reports/eval-2026-11-26.json`。

## 智能账单问答（NL → SQL）

`POST /api/ai/ask`，请求 `{"question": "这个月吃饭花了多少"}`，返回中文回答、命中的 SQL 与结果集。

链路：问题 → 模型生成 SQL（Prompt 内置表结构、分类树与「本月/上月/近三个月」的日期区间，避免模型自己算日期）→ `SqlGuard` 硬校验 → 参数化只读执行 → 模型把结果转成一句话。

模型输出默认不可信，因此有三层约束：

- `SqlGuard`：只允许单条 `SELECT`，禁止写操作/DDL/UNION/注释/多语句，`FROM`/`JOIN` 仅白名单表 `bill`
- 用户隔离：SQL 必须含 `openid = :openid`，openid 由服务端以命名参数绑定，模型无法指定他人数据
- 执行侧：自动补 `deleted = 0`、LIMIT 收敛到 200、3 秒查询超时、只读事务；回答模型失败时用结果集模板兜底

在 60 条自建问答集上的实测（32 条固定种子账单，日期按「相对当前月」生成，换月重跑结果一致）：

| 指标 | 结果 |
|---|---|
| 查询执行准确率 | 100%（60/60） |
| 回答准确率 | 100%（60/60） |
| 平均延迟 | 1807 ms |
| P95 延迟 | 2445 ms |

复现：

```bash
# 1. 生成种子账单与 60 条问句
python backend/tools/eval/gen_ask_dataset.py

# 2. 跑评测（写入评测专用 openid 的账单，不影响真实用户数据）
java -jar target/smart-ledger-backend-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.main.web-application-type=none \
  --smartledger.eval.ask-enabled=true \
  --smartledger.ai.enabled=true \
  --smartledger.ai.rate-limit-per-minute=0
```

完整结果见 `backend/tools/eval/reports/ask-eval-2026-11-26.json`。单测：`./mvnw test`（24 个用例，CI 每次提交执行）。

## 快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE smart_ledger DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql --default-character-set=utf8mb4 -uroot -p smart_ledger < "Accounting system/backend/src/main/resources/db/schema.sql"
mysql --default-character-set=utf8mb4 -uroot -p smart_ledger < "Accounting system/backend/src/main/resources/db/data.sql"
```

导入后 `category` 应有 68 条（14 个一级 + 54 个二级），`pay_channel` 5 条。

### 2. 启动后端

先创建本地私有配置（该文件已被 `.gitignore` 排除，不会入库）：

```yaml
# Accounting system/backend/src/main/resources/application-local.yml
smartledger:
  wx:
    app-id: ""
    app-secret: ""
    mock-enabled: true      # 本地开发走 mock 登录，无需真实微信凭证
  ai:
    api-key: ""             # 留空则 AI 解析不可用，手动记账不受影响
    rag:
      enabled: false        # 检索增强，需同时配置 DASHSCOPE_API_KEY
  vision:
    api-key: ""             # 拍照识别，留空则只保存图片
```

```bash
cd "Accounting system/backend"
./mvnw spring-boot:run
# 或先打包：./mvnw clean package -DskipTests && java -jar target/smart-ledger-backend-1.0.0.jar
```

服务默认监听 `8080`，健康检查：`curl http://localhost:8080/api/health`。

### 3. 运行小程序

1. 微信开发者工具导入 `Accounting system/miniprogram`
2. 确认 `config/env.js` 中 `ENV = 'dev'`，接口指向 `http://127.0.0.1:8080`
3. 本地调试勾选「不校验合法域名」

## 环境变量

生产（`prod`）环境全部通过环境变量注入，模板见 `deploy/env/smart-ledger.env.example`：

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | JWT 签名密钥，建议 `openssl rand -base64 48` 生成 |
| `WX_APP_ID` / `WX_APP_SECRET` | 微信小程序 AppID / AppSecret（18 位 / 32 位） |
| `DEEPSEEK_API_KEY` | DeepSeek 密钥，留空则 AI 文字解析不可用，其余功能正常 |
| `DASHSCOPE_API_KEY` | 通义千问视觉模型密钥，留空则拍照识别回落为手动填写 |
| `AI_ENABLED` | 文本解析开关，`false` 时走本地规则（生产默认关闭） |
| `VISION_ENABLED` | 拍照识别开关（生产需显式设为 `true`） |
| `AI_RAG_ENABLED` | 检索增强开关，需配置 `DASHSCOPE_API_KEY` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接信息 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号密码 |
| `UPLOAD_DIR` | 账单图片存放目录 |

## 部署

`deploy/` 目录提供了一套可直接复用的部署配置：

```bash
# 1. 服务器初始化（JDK 17 / MySQL 8 / Nginx / 运行用户 / systemd）
bash deploy/server-setup.sh

# 2. 上传 jar 与配置
scp backend/target/smart-ledger-backend-1.0.0.jar root@<server>:/opt/smart-ledger/

# 3. 环境变量（缺一项启动即失败）
cp deploy/env/smart-ledger.env.example /etc/smart-ledger/env
chmod 600 /etc/smart-ledger/env

# 4. Nginx 站点与证书
cp deploy/nginx/*.conf /etc/nginx/conf.d/
nginx -t && systemctl reload nginx
```

## 安全约定

- 密钥一律走环境变量或本地私有配置文件，仓库中只保留 `*.example` 模板
- `application-local.yml`、`application-prod.yml`、证书私钥、`project.private.config.json`、`uploads/` 均在 `.gitignore` 中
- 生产环境仅暴露 80/443，后端 8080 只监听本机，由 Nginx 反向代理

## 文档

`docs/` 下包含需求规格、概要设计、数据库设计、接口设计、测试计划、迭代规划与 UI 交互设计，可作为了解系统设计意图的入口。

## License

本项目仅用于学习与个人使用。
