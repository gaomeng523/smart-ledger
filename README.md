# 萌萌记账 · smart-ledger

一个面向个人的智能记账系统：微信小程序前端 + Spring Boot 后端。支持用一句自然语言快速记账，AI 自动识别金额、分类与支付渠道，并提供日历账单、分类统计、预算提醒、周期订阅、心愿储蓄等完整闭环。

## 功能

- **AI 快速记账**：输入「午饭 30 微信」即可自动解析金额、收支类型、分类、支付渠道与日期，解析结果可二次编辑；解析失败自动降级为手动表单
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
| AI | DeepSeek Chat API（自然语言账单解析） |
| 前端 | 微信小程序原生（WXML / WXSS / JavaScript），自定义组件化、深色设计令牌 |
| 部署 | Nginx（HTTPS 反向代理）+ systemd + 腾讯云轻量服务器 |

## 目录结构

```
.
├── docs/                          需求、概要设计、数据库、接口、测试、UI 等文档
└── Accounting system/
    ├── backend/                   Spring Boot 后端
    │   ├── src/main/java/com/smartledger/
    │   │   ├── controller/        14 个 REST 控制器
    │   │   ├── service/           业务逻辑
    │   │   ├── ai/                DeepSeek 调用、提示词构建、解析结果校验
    │   │   ├── mapper/            MyBatis-Plus 数据访问
    │   │   ├── entity/ dto/       实体与传输对象
    │   │   ├── config/            拦截器、缓存、静态资源映射
    │   │   └── common/            统一响应、异常处理、工具类
    │   └── src/main/resources/
    │       ├── db/schema.sql      建表脚本（可重复执行）
    │       ├── db/data.sql        字典数据（分类、支付渠道）
    │       └── application*.yml   多环境配置
    ├── miniprogram/               微信小程序
    │   ├── pages/                 15 个页面（首页、日历、统计、我的、登录等）
    │   ├── components/            自定义组件（账单表单、分类选择器、空态、抽屉等）
    │   ├── utils/                 请求封装、登录鉴权、存储、格式化
    │   └── config/env.js          环境与接口地址配置
    └── deploy/                    部署套件
        ├── nginx/                 站点配置（HTTPS + 反向代理）
        ├── systemd/               服务单元文件
        ├── env/                   环境变量模板
        └── server-setup.sh        服务器初始化脚本
```

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
| `DEEPSEEK_API_KEY` | DeepSeek 密钥，留空则 AI 解析不可用，其余功能正常 |
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
