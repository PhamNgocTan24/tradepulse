# Kafka là gì? Hướng dẫn cực kỳ dễ hiểu cho người mới bắt đầu

Chào mừng bạn đến với hành trình học tập về hệ thống phân tán! Khi xây dựng các ứng dụng lớn (như ứng dụng mô phỏng giao dịch TradePulse này), chúng ta không thể để các phần mềm gọi điện trực tiếp cho nhau mãi được. Đó là lý do **Kafka** ra đời.

Hãy đọc bài này với tâm thế thoải mái nhất, không cần phải nhồi nhét quá nhiều từ ngữ chuyên ngành nhé!

---

## 1. Hình ảnh ẩn dụ: Bưu điện siêu tốc của thành phố

Để hiểu Kafka, hãy quên máy tính đi một chút. Hãy tưởng tượng bạn đang quản lý một **Bưu điện trung tâm** rất lớn trong thành phố. 

*   Có những người chỉ chuyên **gửi thư** đi (gọi là **Producers**).
*   Có những người chỉ chuyên **nhận thư** về đọc (gọi là **Consumers**).
*   Bưu điện chia thư vào các **Hộp thư phân loại** khác nhau (gọi là **Topics**).

---

## 2. Các khái niệm cốt lõi bằng ngôn ngữ đời thường

### 📬 Producer & Consumer (Người gửi và Người nhận)
*   **Producer (Người gửi):** Giống như một người viết thư tay và ném vào thùng thư. Trong dự án TradePulse, dịch vụ lấy giá coin (`market-data-service`) chính là Producer. Nó liên tục gửi giá Bitcoin mới vào bưu điện.
*   **Consumer (Người nhận):** Giống như người đăng ký nhận báo hàng ngày. Họ chỉ ngồi đợi bưu tá mang thư đến tận nhà. Trong TradePulse, bộ máy khớp lệnh (`matching-engine`) là Consumer, nó ngồi hóng xem có giá coin mới không để khớp lệnh cho khách hàng.

### 📁 Topic (Hộp thư phân loại chuyên đề)
Bạn không thể trộn lẫn hóa đơn điện với thư tình của khách hàng. Bạn chia bưu điện thành các hòm thư chuyên đề:
*   Hòm thư `market-data`: Chỉ nhận thư về giá cả thị trường.
*   Hòm thư `order-events`: Chỉ nhận thư về mua bán, đặt lệnh.
*   Hòm thư `notifications`: Chỉ nhận thư yêu cầu gửi email/SMS báo tin.

*Điểm đặc biệt:* Ở bưu điện thường, bạn lấy thư đi là hòm thư trống rỗng. Nhưng ở **Bưu điện Kafka**, thư đã đọc vẫn nằm nguyên trong hòm thư một vài ngày (ví dụ: 7 ngày). Nếu người nhận ngủ quên hoặc máy tính bị hỏng, khi dậy họ vẫn có thể đọc lại toàn bộ thư cũ từ đầu!

### 🧱 Partition (Chia nhỏ hộp thư để không bị tắc nghẽn)
Nếu bưu điện chỉ có duy nhất **một** khe bỏ thư cho hòm thư `market-data`, mà hàng triệu người cùng ùa vào bỏ thư cùng lúc, bưu điện sẽ tắc nghẽn hoàn toàn.

Để giải quyết, bưu điện làm ra **10 khe bỏ thư song song** (gọi là 10 **Partitions**).
*   Thư gửi đến sẽ được chia đều vào 10 khe này.
*   10 người nhận thư có thể đứng ở 10 khe và rút thư cùng một lúc. Nhờ vậy tốc độ xử lý nhanh lên gấp 10 lần!

### 🏢 Broker (Cơ sở bưu điện / Máy chủ)
Mỗi bưu cục vật lý chính là một **Broker**. 
*   Nếu hệ thống chỉ chạy 1 Broker (như môi trường local của bạn), nghĩa là bạn chỉ có 1 bưu cục duy nhất giữ toàn bộ các hòm thư.
*   Nếu chạy thật (Production), người ta sẽ dựng 3 bưu cục (3 Brokers) ở 3 quận khác nhau. Thư gửi vào bưu cục này sẽ được tự động sao chép sang các bưu cục khác. Nhỡ một bưu cục bị cháy (sập máy chủ), bưu điện vẫn hoạt động bình thường!

### 🕵️‍♂️ Zookeeper (Người quản đốc bưu điện)
Zookeeper không bốc xếp thư, không chuyển thư. Zookeeper là **người quản đốc ngồi trong văn phòng**:
*   Theo dõi xem bưu cục nào đang mở cửa, bưu cục nào bị mất điện để điều phối.
*   Đứng ra phân công xem ai là người chịu trách nhiệm chính (Leader) cho từng khe thư (Partition).
*   Nếu "trưởng nhóm bưu cục" bị ốm, Zookeeper sẽ chọn ngay một người khác lên thay trong vòng vài tích tắc.

---

## 3. Tại sao chúng ta lại cần nó? (Lợi ích thực tế)

1.  **Chống sập nguồn dây chuyền (Decoupling):** Nếu hệ thống khớp lệnh bị quá tải hoặc sập trong 10 phút, người dùng vẫn bấm nút đặt lệnh mua bán bình thường. Lệnh được lưu an sau trong Kafka. Khi hệ thống khớp lệnh hoạt động trở lại, nó chỉ cần lôi đống lệnh đang xếp hàng trong Kafka ra xử lý tiếp. Không một giao dịch nào bị mất!
2.  **Tốc độ cực nhanh (High Throughput):** Kafka được tối ưu hóa để ghi dữ liệu cực nhanh xuống đĩa cứng theo dạng xếp hàng nối đuôi nhau (Sequential I/O), giúp nó xử lý hàng triệu message/giây dễ dàng.
3.  **Chia sẻ dữ liệu dễ dàng:** Một bức thư gửi vào Topic `order-events` có thể được cùng lúc đọc bởi `portfolio-service` (để trừ tiền ví) và `reporting-service` (để in hóa đơn) mà không bên nào tranh giành hay ảnh hưởng đến bên nào.
