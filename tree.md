```
hmap-backend
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── hmap
│   │   │           └── backend
│   │   │               ├── BackendApplication.java
│   │   │               │
│   │   │               ├── config
│   │   │               │   └── CorsConfig.java
│   │   │               │
│   │   │               ├── exception
│   │   │               │   ├── GlobalExceptionHandler.java
│   │   │               │   ├── ResourceNotFoundException.java
│   │   │               │   └── BadRequestException.java
│   │   │               │
│   │   │               ├── security
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   ├── JwtFilter.java
│   │   │               │   ├── JwtService.java
│   │   │               │   └── CustomUserDetailsService.java
│   │   │               │
│   │   │               ├── auth
│   │   │               │   ├── controller
│   │   │               │   │   └── AuthController.java
│   │   │               │   ├── dto
│   │   │               │   │   ├── LoginRequest.java
│   │   │               │   │   ├── RegisterRequest.java
│   │   │               │   │   └── AuthResponse.java
│   │   │               │   └── service
│   │   │               │       └── AuthService.java
│   │   │               │
│   │   │               ├── user
│   │   │               │   ├── controller
│   │   │               │   │   └── UserController.java
│   │   │               │   ├── dto
│   │   │               │   │   ├── UserRequest.java
│   │   │               │   │   └── UserResponse.java
│   │   │               │   ├── entity
│   │   │               │   │   └── User.java
│   │   │               │   ├── repository
│   │   │               │   │   └── UserRepository.java
│   │   │               │   ├── service
│   │   │               │   │   └── UserService.java
│   │   │               │   ├── mapper
│   │   │               │   │   └── UserMapper.java
│   │   │               │   └── enums
│   │   │               │       └── RoleType.java
│   │   │               │
│   │   │               ├── room
│   │   │               │   ├── controller
│   │   │               │   │   └── RoomController.java
│   │   │               │   ├── dto
│   │   │               │   │   ├── RoomRequest.java
│   │   │               │   │   └── RoomResponse.java
│   │   │               │   ├── entity
│   │   │               │   │   └── Room.java
│   │   │               │   ├── repository
│   │   │               │   │   └── RoomRepository.java
│   │   │               │   ├── service
│   │   │               │   │   └── RoomService.java
│   │   │               │   ├── mapper
│   │   │               │   │   └── RoomMapper.java
│   │   │               │   └── enums
│   │   │               │       └── RoomStatus.java
│   │   │               │
│   │   │               ├── reservation
│   │   │               │   ├── controller
│   │   │               │   │   └── ReservationController.java
│   │   │               │   ├── dto
│   │   │               │   │   ├── ReservationRequest.java
│   │   │               │   │   └── ReservationResponse.java
│   │   │               │   ├── entity
│   │   │               │   │   └── Reservation.java
│   │   │               │   ├── repository
│   │   │               │   │   └── ReservationRepository.java
│   │   │               │   ├── service
│   │   │               │   │   └── ReservationService.java
│   │   │               │   ├── mapper
│   │   │               │   │   └── ReservationMapper.java
│   │   │               │   └── enums
│   │   │               │       └── ReservationStatus.java
│   │   │               │
│   │   │               └── admin
│   │   │                   ├── controller
│   │   │                   │   └── AdminController.java
│   │   │                   ├── dto
│   │   │                   │   └── AdminUserResponse.java
│   │   │                   └── service
│   │   │                       └── AdminService.java
│   │   │
│   │   └── resources
│   │       ├── application.properties
│   │       └── db
│   │           └── migration
│   │               └── V1__init_schema.sql
│   │
│   └── test
│       └── java
│           └── com
│               └── hmap
│                   └── backend
│                       └── BackendApplicationTests.java
│
├── pom.xml
└── .gitignore
'''