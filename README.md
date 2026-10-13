# Jobbeacon Backend

Backend service cho hệ thống Jobbeacon: thu thập, lưu trữ và phân loại tin tuyển dụng (job posting), xây dựng bằng Spring Boot 3 (Java 21) + PostgreSQL (pgvector) + RabbitMQ.

## Yêu cầu môi trường

- Java 21
- Maven 3.9+
- PostgreSQL 16+ **có cài extension [pgvector](https://github.com/pgvector/pgvector)** (dùng image `pgvector/pgvector:pg16` nếu chạy Postgres bằng Docker — Postgres thường không có sẵn extension này)
- RabbitMQ
- Firebase service account (dùng cho xác thực)
- Gemini API key (tuỳ chọn — chỉ cần nếu muốn bật tính năng tự động phân loại category cho job posting bằng embedding)

## Cấu hình (biến môi trường)

Toàn bộ cấu hình mặc định nằm ở [`src/main/resources/application.yml`](src/main/resources/application.yml), override qua biến môi trường:

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `PROFILE_ACTIVE` | `local` | Spring profile |
| `DB_URL` | `jdbc:postgresql://localhost:5432/job_platform` | JDBC URL Postgres |
| `DB_USERNAME` | `postgres` | User DB |
| `DB_PASSWORD` | `postgres` | Password DB |
| `LIQUIBASE_ENABLED` | `true` | Bật/tắt chạy migration Liquibase khi start |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Kết nối RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Auth RabbitMQ |
| `CORS_ORIGINS` | `http://localhost:5173` | Origin được phép gọi API |
| `FIREBASE_CREDENTIALS_PATH` | `firebase-service-account.json` | Đường dẫn file service account Firebase (classpath hoặc filesystem) |
| `GEMINI_API_KEY` | *(rỗng)* | API key Gemini — cần để tính năng phân loại category tự động hoạt động |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-2` | Model embedding dùng để encode job posting |
| `GEMINI_EMBEDDING_DIMENSION` | `1536` | Số chiều embedding (phải khớp với embedding của category) |
| `CATEGORY_CLASSIFICATION_MIN_SCORE` | `0.72` | Ngưỡng điểm cosine similarity tối thiểu để chọn category |
| `CATEGORY_CLASSIFICATION_MIN_MARGIN` | `0.03` | Ngưỡng chênh lệch điểm tối thiểu giữa Top 1 và Top 2 |
| `PORT` | `8080` | Cổng HTTP server |

## Cài đặt & chạy

### 1. Chuẩn bị database

Tạo database Postgres (image có sẵn pgvector), ví dụ bằng Docker:

```bash
docker run -d --name jobbeacon-db \
  -e POSTGRES_DB=job_platform \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

Khi app start lần đầu, Liquibase sẽ tự chạy migration để tạo schema, bật extension `vector`, và seed sẵn cây category (chưa có embedding).

### 2. Firebase credentials

Đặt file `firebase-service-account.json` ở thư mục gốc repo (hoặc set `FIREBASE_CREDENTIALS_PATH` trỏ tới vị trí khác). File này **không** được commit vào git.

### 3. Tải file category embeddings (bắt buộc để phân loại category hoạt động)

Cây category đã được seed sẵn qua Liquibase, nhưng **vector embedding của từng category** không nằm trong git (file JSON ~50MB) — cần tải thủ công:

1. Tải file tại: **https://drive.google.com/file/d/1ZNWiMgbpShkZSqcinJ1LQwRWRtzsW3bv/view?usp=sharing**
2. Đặt file đã tải vào đúng đường dẫn sau, đổi tên thành `src/main/resources/db/changelog/data/category_embeddings.json` nếu cần:
   ```
   src/main/resources/data/category_embeddings.json
   ```

Khi app khởi động, [`CategoryEmbeddingInitializer`](src/main/java/com/jobbeacon/configuration/CategoryEmbeddingInitializer.java) sẽ tự đọc file này, match từng category theo cột `path`, và ghi embedding vào DB. Cơ chế này **idempotent** — nếu tất cả category đã có embedding thì sẽ tự bỏ qua ở các lần start sau, nên có thể xoá file JSON khỏi máy sau khi chạy thành công lần đầu.

> Nếu không cung cấp file này, app vẫn chạy bình thường nhưng tính năng tự động phân loại category cho job posting (`processJobPostingMessage`) sẽ không chọn được category nào do không có embedding để so sánh.

### 4. RabbitMQ

Chạy RabbitMQ (mặc định trỏ tới `localhost:5672`, user/pass `guest`/`guest`):

```bash
docker run -d --name jobbeacon-mq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

### 5. Build & chạy

```bash
mvn clean package
java -jar target/jobbeacon-be.jar
```

Hoặc chạy trực tiếp khi dev:

```bash
mvn spring-boot:run
```

Hoặc build & chạy bằng Docker (xem [`Dockerfile`](Dockerfile)):

```bash
docker build -t jobbeacon-be .
docker run -p 8080:8080 --env-file .env jobbeacon-be
```

Mặc định app chạy ở `http://localhost:8080`, API docs (Swagger/OpenAPI) tại `/swagger-ui.html`.

## Tìm kiếm job

Danh sách job đọc Elasticsearch. Chi tiết job vẫn đọc Postgres. Phần đồng bộ index nằm ở service riêng [`../Jobbeacon-sync`](../Jobbeacon-sync).

Postgres dùng cho CDC là service `jobbeacon-db` trong `infras-deployment`, cổng host `5433`, `wal_level=logical`. Container Postgres khác đang chiếm cổng `5432` không dùng cho luồng này.

```bash
DB_URL=jdbc:postgresql://localhost:5433/job_platform \
ELASTICSEARCH_URL=http://localhost:9200 \
mvn spring-boot:run
```

`SEARCH_BACKFILL_ON_STARTUP=true` ghi một hàng outbox cho mỗi job hiện có. CDC kéo các hàng đó lên index `jobs`.
