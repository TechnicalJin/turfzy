# Turfzy — Master Development Roadmap

---

### **Phase 1: Project Setup & Foundation**  
**Duration:** Week 1-2

**1.1 Backend — Spring Boot Project Init**  
- Project Creation: Use start.spring.io → Java 17, Maven, JAR packaging  
- Dependencies: Spring Web, Spring Data JPA, Spring Security, MySQL Driver, Lombok, Validation (jakarta), Spring Boot DevTools, Actuator, OAuth2 Client, Java JWT (jjwt 0.11+)  
- Folder Structure: `auth/`, `user/`, `turf/`, `booking/`, `payment/`, `notification/`, `common/`  
- Important Setting: `spring.jpa.hibernate.ddl-auto=validate` (use update only in dev)

**1.2 MySQL Database Setup**  
- MySQL 8.x + schema creation with utf8mb4_unicode_ci  
- Full application.yml configuration shown  
- HikariCP settings (max pool 10, timeout 20000)

**1.3 Frontend — React Project Init**  
- Vite React template command  
- Full list of packages: axios, zustand, react-router-dom@6, react-query (tanstack), tailwindcss, shadcn/ui, react-hook-form + zod, dayjs, razorpay, socket.io-client  
- Tailwind colors: Primary `#1E90FF`, Accent `#FF6800`, Background dark `#0F172A`

**1.4 Git Repo + Branch Strategy**  
- 2 separate repos, branch naming convention, .gitignore details

**1.5 Full Database Schema Design**  
**Every table exactly as given:**
- USERS (full columns)
- ROLES
- USER_ROLES
- TURF (sport_type SET, amenities JSON, etc.)
- TURF_IMAGES
- TIME_SLOT (with UNIQUE KEY)
- BOOKING
- PAYMENT (razorpay fields)
- REVIEWS
- NOTIFICATIONS  
**All recommended indexes listed**

---

### **Phase 2: Authentication & Security Layer**  
**Duration:** Week 2-3

**Every section preserved exactly:**
- SecurityFilterChain full config
- JWT Implementation (access 15min, refresh 7days, filter behavior, exceptions)
- Google OAuth2 (yml config, CustomOAuth2UserService, SuccessHandler)
- Local Login & Registration (full flow, BCrypt strength=12, no auto-login)
- RBAC + PBAC (TurfPolicy, BookingPolicy examples)
- Frontend: Zustand store (in-memory only), Axios interceptors, ProtectedRoute

---

### **Phase 3: Core Features — Turf & Booking**  
**Duration:** Week 3-5

- Turf CRUD + all query parameters
- Image Upload (Cloudinary, max 6, validation)
- Time Slot Generation (hybrid strategy + scheduler)
- **Booking Creation — Race Condition Prevention** (3 layers explained in detail)
- Booking Cancellation (24hr rule, refund logic)
- Frontend: Turf Listing Page + Turf Detail Page (all components, WebSocket subscription)

---

### **Phase 4: Payment + Dashboards + Real-time**  
**Duration:** Week 5-7

- Razorpay full integration (SDK, order, verify, webhook, frontend flow)
- WebSocket (STOMP config + frontend @stomp/stompjs)
- **All three dashboards** (Admin, Owner, Customer) with every page and feature listed

---

### **Phase 5: Advanced Features & Performance**  
**Duration:** Week 7-9

- Redis caching + distributed lock
- Email Notifications (Thymeleaf, @Async)
- Input Validation + Global Exception Handler (full error format)
- Rate Limiting (Bucket4j), Security headers, XSS protection
- Reviews & Ratings logic
- Testing strategy (Unit, Integration, Frontend)

---

### **Phase 6: Deployment & Production Readiness**  
**Duration:** Week 9–11

**6.1 Docker setup**  
- Multi-stage Dockerfile (exact commands)  
- docker-compose.yml details (services, volumes, health checks)

**6.2 CI/CD with GitHub Actions**  
- Backend and Frontend workflow steps  
- Secrets list

**6.3 Production deployment**  
- Frontend: Vercel  
- Backend: AWS EC2 / Render  
- DB: AWS RDS (Multi-AZ)  
- Redis: ElastiCache  
- Images: Cloudinary  
- Nginx config details (WebSocket proxying, SSL)

**6.4 Monitoring & logging**  
- Actuator endpoints  
- Structured logging with MDC  
- Sentry + UptimeRobot

---