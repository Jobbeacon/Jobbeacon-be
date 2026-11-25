# Admin API

Tất cả endpoint nằm dưới `/api/v1/admin/**`, yêu cầu Firebase token **và** user trong DB có `role = ADMIN`, `status = ACTIVE` (kiểm tra theo DB, không theo custom claim). Không phải admin → `403`.

Quy ước chung:

- Bọc response trong `ResponseGeneral` (`{status, message, data, timestamp}`), JSON **snake_case** (theo `spring.jackson.property-naming-strategy`).
- Phân trang: query `page` (bắt đầu từ 1), `size` (mặc định 20). Trả `PageResponse` = `{content: [...], amount: <tổng số bản ghi>}`.
- Thời gian dạng ISO-8601 (`Instant`).
- Lỗi nghiệp vụ: `409` (trùng tên, còn ràng buộc), `404`, `400`. Body lỗi: `data = { code, detail }` (detail đã dịch theo `Accept-Language`).
- `POST` tạo mới trả HTTP 200 với `status: 201` trong body (giống `ResponseGeneral.ofCreated` hiện có). `DELETE` trả `data: null`.
- `created_at` của company/skill/category: các bảng này không có cột thời gian, lấy từ timestamp của UUID v7 (`id`). UUID không phải v7 → `null`.
- Bootstrap admin: property `app.admin.emails` (danh sách email, phân tách dấu phẩy, env `APP_ADMIN_EMAILS`). Khi sync user từ Firebase, email nằm trong danh sách được set `role = ADMIN`.
- User `SUSPENDED`/`DELETED` gọi bất kỳ API cần đăng nhập nào → `403`.
- `GET /api/v1/users/me` trả thêm field `role`.

## Tổng quan crawl

`GET /api/v1/admin/overview?days=30`

Mỗi dòng `job_source_mapping` là một lần ghi nhận (cùng tin ở 2 nguồn tính 2 lần). Theo giờ `Asia/Ho_Chi_Minh`.

```json
{
  "sources": ["topcv.vn", "itviec.com"],
  "today": "2026-11-13",
  "total_window": 1234,
  "total_today": 56,
  "total_month": 789,
  "daily": [{ "date": "2026-10-15", "total": 40, "by_source": { "topcv.vn": 30, "itviec.com": 10 } }],
  "monthly": [{ "month": "2026-06", "total": 900, "by_source": { "topcv.vn": 700, "itviec.com": 200 } }],
  "hours": [0, 3, 1, "... 24 phần tử, đếm theo giờ crawled_at trong cửa sổ days"],
  "quiet_sources": ["itviec.com"],
  "recent": [{
    "crawled_at": "...", "domain": "topcv.vn", "job_url": "https://...",
    "job_id": "uuid", "job_title": "...", "company_id": "uuid", "company_name": "..."
  }]
}
```

`daily` đủ `days` ngày (kể cả ngày 0 bản ghi), `monthly` 6 tháng gần nhất, `recent` 12 dòng mới nhất, `quiet_sources` = nguồn chưa có ghi nhận hôm nay.

## Việc làm

`GET /api/v1/admin/jobs/stats` → `{ "total": 0, "open": 0, "expired": 0, "crawled_today": 0 }`

`GET /api/v1/admin/jobs?q=&source=&employment_type=&company_id=&crawled=today|month&status=open|expired&sort=new|old|expiring|salary&page=1&size=20`

- `q`: tìm trong tiêu đề, tên công ty, location (không phân biệt hoa thường).
- `sort`: `new` (mặc định, crawl gần nhất giảm dần), `old`, `expiring` (hạn nộp gần nhất còn hiệu lực trước), `salary` (max_salary giảm dần, null cuối).

Phần tử:

```json
{
  "id": "uuid", "title": "...", "company_id": "uuid", "company_name": "...",
  "locations": ["Hà Nội"], "min_salary": 10000000, "max_salary": 20000000,
  "currency": "VND", "salary_period": "MONTH", "employment_type": "FULL_TIME",
  "posted_date": "...", "expired_date": "...",
  "sources": ["topcv.vn"], "last_crawled_at": "..."
}
```

`GET /api/v1/admin/jobs/{id}` → các field trên + `description`, `benefits`, `experience_months`,
`skills: [{id, name}]`, `categories: [{id, name, path: ["Cấp 1", "Cấp 2", "Cấp 3"]}]`,
`mappings: [{domain, job_url, crawled_at}]`.

`PUT /api/v1/admin/jobs/{id}`

```json
{
  "title": "...", "company_id": "uuid", "employment_type": "FULL_TIME", "experience_months": 12,
  "category_ids": ["uuid"], "skill_ids": ["uuid"], "locations": ["Hà Nội"],
  "min_salary": 1, "max_salary": 2, "currency": "VND", "salary_period": "MONTH",
  "posted_date": "...", "expired_date": "...", "description": "...", "benefits": "..."
}
```

`title`, `company_id` bắt buộc; `max_salary >= min_salary`. Đổi `locations` thì dựng lại `job_location` như luồng crawler. Ghi outbox `updated`. Trả về chi tiết như GET.

`DELETE /api/v1/admin/jobs/{id}` → xóa job cùng mapping/location/skill/category, ghi outbox `deleted`.

## Công ty

`GET /api/v1/admin/companies/stats` → `{ "total": 0, "with_logo": 0, "without_jobs": 0 }`

`GET /api/v1/admin/companies?q=&sort=jobs|recent|name&page=1&size=20`

```json
{ "id": "uuid", "name": "...", "logo_url": "...", "job_count": 10, "open_job_count": 4,
  "sources": ["topcv.vn"], "created_at": "...", "last_crawled_at": "..." }
```

`GET /api/v1/admin/companies/{id}` → field trên + `locations: ["Hà Nội"]` + `possible_duplicates: [{id, name, job_count}]`
(trùng sau khi chuẩn hóa tên: bỏ dấu, lowercase, bỏ các từ "công ty", "cổ phần", "tnhh", "jsc", "co., ltd", "việt nam"...).

`PUT /api/v1/admin/companies/{id}` `{ "name": "...", "logo_url": "..." }` → `409` nếu trùng tên (ignore case) với công ty khác. Ghi outbox `updated` cho các job của công ty.

`POST /api/v1/admin/companies/{id}/merge` `{ "target_id": "uuid" }` → chuyển tất cả job sang target, nếu target chưa có logo thì lấy logo nguồn, xóa công ty nguồn. Ghi outbox `updated` cho job bị chuyển. Trả chi tiết target.

`DELETE /api/v1/admin/companies/{id}` → `409` nếu còn job.

## Người dùng

`GET /api/v1/admin/users/stats` → `{ "total": 0, "admins": 0, "active": 0, "suspended": 0, "with_cv": 0 }`

`GET /api/v1/admin/users?q=&role=ADMIN|USER&status=ACTIVE|SUSPENDED|DELETED&page=1&size=20`

```json
{ "id": "uuid", "email": "...", "display_name": "...", "avatar_url": "...",
  "role": "USER", "status": "ACTIVE", "has_cv": true,
  "created_at": "...", "last_login_at": "..." }
```

`GET /api/v1/admin/users/{id}` → field trên +
`cv: { current_title, total_months_experience, skills: ["Java"], updated_at } | null` +
`preference: { desired_titles: [], employment_types: [], provinces: ["Hà Nội"], expected_salary_min, expected_salary_max, currency, salary_period, salary_negotiable, remote_ok, open_to_work } | null`.

`PUT /api/v1/admin/users/{id}` `{ "display_name": "...", "role": "ADMIN", "status": "ACTIVE" }`
→ `409` nếu thao tác làm hệ thống không còn admin ACTIVE nào. Trả chi tiết.

## Danh mục ngành

`GET /api/v1/admin/categories` → cây đầy đủ:

```json
[{ "id": "uuid", "name": "...", "level": 1, "job_count": 120, "children": [ ... ] }]
```

`job_count` của node cha = tổng số job (distinct) gán vào chính nó và mọi node con.

`POST /api/v1/admin/categories` `{ "name": "...", "parent_id": "uuid|null" }` → tối đa 3 cấp (`400`); `409` nếu trùng tên cùng cha. Trả node mới `{id, name, level, job_count: 0, children: []}`.
`PUT /api/v1/admin/categories/{id}` `{ "name": "..." }` → `409` nếu trùng tên cùng cha. Trả node (kèm cây con). Cập nhật `path` của mọi mục con.
`DELETE /api/v1/admin/categories/{id}` → `409` nếu còn mục con; gỡ mapping job.
Tạo/đổi tên: tính lại embedding (nếu có client embedding) hoặc để null để initializer xử lý.

## Kỹ năng

`GET /api/v1/admin/skills/stats` → `{ "total": 0, "recent": 0, "without_alias": 0, "unused": 0 }`

`GET /api/v1/admin/skills?q=&filter=recent|noalias|unused&sort=jobs|recent|name&page=1&size=20`

- `q` khớp tên hoặc alias. `recent` = tạo trong 7 ngày (theo UUID v7).

```json
{ "id": "uuid", "name": "Java", "aliases": ["java core"], "job_count": 10, "cv_count": 2, "created_at": "..." }
```

`POST /api/v1/admin/skills` `{ "name": "...", "aliases": ["..."] }`
`PUT /api/v1/admin/skills/{id}` `{ "name": "...", "aliases": ["..."] }` (thay toàn bộ alias)
→ `409` nếu tên/alias đã là tên hoặc alias của kỹ năng khác (so theo normalized).

`POST /api/v1/admin/skills/{id}/merge` `{ "target_id": "uuid" }` → tên + alias nguồn thành alias của target, chuyển job/CV mapping (bỏ trùng), xóa nguồn, ghi outbox `updated` cho job bị ảnh hưởng. Trả target.

`DELETE /api/v1/admin/skills/{id}` → gỡ mapping job/CV + alias rồi xóa; ghi outbox `updated` cho job bị ảnh hưởng.
