-- =====================================================================
-- 1. 회원·조직
-- =====================================================================

CREATE TABLE members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    oauth_provider VARCHAR(20) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    platform_role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_members_oauth UNIQUE (oauth_provider, oauth_subject)
);

CREATE TABLE organizations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_type VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    business_number VARCHAR(20) UNIQUE,
    representative_name VARCHAR(50),
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE organization_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    organization_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organization_members UNIQUE (organization_id, member_id)
);

-- =====================================================================
-- 2. 행사
-- =====================================================================

-- organizer_organization_id: 명세서 2.1 표에는 누락되어 있으나
-- 13번 인덱스 목록과 14번 관계 요약에 근거해 추가함 (사용자 확인 완료)
CREATE TABLE events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organizer_organization_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    short_description VARCHAR(300),
    description TEXT NOT NULL,
    venue_name VARCHAR(200) NOT NULL,
    address VARCHAR(300) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    ticket_sales_start_at TIMESTAMPTZ,
    ticket_sales_end_at TIMESTAMPTZ,
    ticket_price NUMERIC(12,0) NOT NULL,
    ticket_total_quantity INTEGER NOT NULL,
    ticket_sold_quantity INTEGER NOT NULL,
    ticket_purchase_limit INTEGER NOT NULL,
    representative_file_id BIGINT,
    status VARCHAR(30) NOT NULL,
    booth_recruitment_enabled BOOLEAN NOT NULL,
    venue_map_enabled BOOLEAN NOT NULL,
    booth_reservation_enabled BOOLEAN NOT NULL,
    no_show_grace_minutes INTEGER NOT NULL,
    rejection_reason TEXT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_events_ticket_price CHECK (ticket_price >= 0),
    CONSTRAINT ck_events_ticket_sold_quantity CHECK (ticket_sold_quantity BETWEEN 0 AND ticket_total_quantity),
    CONSTRAINT ck_events_ticket_purchase_limit CHECK (ticket_purchase_limit > 0),
    CONSTRAINT ck_events_no_show_grace_minutes CHECK (no_show_grace_minutes >= 0)
);

CREATE TABLE event_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    event_role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_members UNIQUE (event_id, member_id)
);

-- =====================================================================
-- 3. 부스 모집·신청
-- =====================================================================

CREATE TABLE booth_recruitments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    recruitment_start_at TIMESTAMPTZ NOT NULL,
    recruitment_end_at TIMESTAMPTZ NOT NULL,
    participant_target TEXT NOT NULL,
    qualification TEXT,
    selection_method TEXT,
    expected_decision_at TIMESTAMPTZ,
    contact_name VARCHAR(50) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    notice TEXT,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE booths (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    assigned_organization_id BIGINT,
    booth_code VARCHAR(30) NOT NULL,
    booth_type VARCHAR(50) NOT NULL,
    floor_name VARCHAR(50),
    zone_name VARCHAR(50),
    location_description VARCHAR(200),
    width_meter NUMERIC(6,2),
    depth_meter NUMERIC(6,2),
    area_sqm NUMERIC(8,2),
    basic_equipment JSONB,
    electricity_available BOOLEAN NOT NULL,
    water_available BOOLEAN NOT NULL,
    drainage_available BOOLEAN NOT NULL,
    internet_available BOOLEAN NOT NULL,
    price NUMERIC(12,0) NOT NULL,
    status VARCHAR(30) NOT NULL,
    display_name VARCHAR(150),
    short_intro VARCHAR(300),
    description TEXT,
    exhibition_content TEXT,
    representative_file_id BIGINT,
    qr_token VARCHAR(100) UNIQUE,
    qr_issued_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booths_event_booth_code UNIQUE (event_id, booth_code),
    CONSTRAINT ck_booths_width_meter CHECK (width_meter >= 0),
    CONSTRAINT ck_booths_depth_meter CHECK (depth_meter >= 0),
    CONSTRAINT ck_booths_area_sqm CHECK (area_sqm >= 0),
    CONSTRAINT ck_booths_price CHECK (price >= 0)
);

CREATE TABLE booth_applications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_no VARCHAR(40) NOT NULL UNIQUE,
    recruitment_id BIGINT NOT NULL,
    booth_id BIGINT NOT NULL,
    applicant_organization_id BIGINT NOT NULL,
    applicant_member_id BIGINT NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    activity_description TEXT NOT NULL,
    exhibition_content TEXT NOT NULL,
    expected_visitors INTEGER,
    electricity_required BOOLEAN NOT NULL,
    water_required BOOLEAN NOT NULL,
    drainage_required BOOLEAN NOT NULL,
    internet_required BOOLEAN NOT NULL,
    application_reason TEXT,
    status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    cancelled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_booth_applications_expected_visitors CHECK (expected_visitors >= 0)
);

CREATE TABLE booth_application_files (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_application_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- =====================================================================
-- 4. 평면도
-- =====================================================================

CREATE TABLE venue_maps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    map_type VARCHAR(20) NOT NULL,
    floor_name VARCHAR(50) NOT NULL,
    image_file_id BIGINT NOT NULL,
    original_width INTEGER NOT NULL,
    original_height INTEGER NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_venue_maps_original_width CHECK (original_width > 0),
    CONSTRAINT ck_venue_maps_original_height CHECK (original_height > 0)
);

CREATE TABLE booth_map_positions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_map_id BIGINT NOT NULL,
    booth_id BIGINT NOT NULL,
    x_ratio NUMERIC(8,6) NOT NULL,
    y_ratio NUMERIC(8,6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_map_positions UNIQUE (venue_map_id, booth_id),
    CONSTRAINT ck_booth_map_positions_x_ratio CHECK (x_ratio BETWEEN 0 AND 1),
    CONSTRAINT ck_booth_map_positions_y_ratio CHECK (y_ratio BETWEEN 0 AND 1)
);

-- =====================================================================
-- 5. 파일·공지·자료실
-- =====================================================================

CREATE TABLE file_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uploaded_by BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_file_assets_file_size CHECK (file_size >= 0)
);

CREATE TABLE event_contents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    resource_type VARCHAR(30),
    audience VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    file_id BIGINT,
    version VARCHAR(20),
    pinned BOOLEAN NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- =====================================================================
-- 6. 티켓 구매·결제
-- =====================================================================

CREATE TABLE payment_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    buyer_member_id BIGINT,
    buyer_name VARCHAR(50),
    buyer_email VARCHAR(255),
    buyer_phone VARCHAR(30),
    order_type VARCHAR(20) NOT NULL,
    total_amount NUMERIC(12,0) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payment_orders_total_amount CHECK (total_amount >= 0)
);

CREATE TABLE ticket_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_order_id BIGINT NOT NULL UNIQUE,
    event_id BIGINT NOT NULL,
    unit_price NUMERIC(12,0) NOT NULL,
    total_quantity INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ticket_orders_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_ticket_orders_total_quantity CHECK (total_quantity > 0)
);

CREATE TABLE payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_order_id BIGINT NOT NULL,
    pg_provider VARCHAR(30) NOT NULL,
    payment_key VARCHAR(200) UNIQUE,
    method VARCHAR(30),
    amount NUMERIC(12,0) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(100),
    failure_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);

CREATE TABLE payment_refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    exchange_code_id BIGINT,
    requester_member_id BIGINT,
    refund_amount NUMERIC(12,0) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    pg_cancel_key VARCHAR(200),
    CONSTRAINT ck_payment_refunds_refund_amount CHECK (refund_amount >= 0)
);

-- =====================================================================
-- 7. 교환 코드·입장 QR
-- =====================================================================

CREATE TABLE exchange_code_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_quantity INTEGER NOT NULL,
    purpose TEXT,
    status VARCHAR(30) NOT NULL,
    reviewed_by BIGINT,
    rejection_reason TEXT,
    reviewed_at TIMESTAMPTZ,
    emailed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_exchange_code_requests_quantity CHECK (requested_quantity > 0)
);

CREATE TABLE exchange_codes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    exchange_code_request_id BIGINT,
    ticket_order_id BIGINT,
    holder_member_id BIGINT,
    code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    redeemed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_exchange_codes_source CHECK (
        (exchange_code_request_id IS NOT NULL AND ticket_order_id IS NULL)
     OR (exchange_code_request_id IS NULL AND ticket_order_id IS NOT NULL)
    )
);

CREATE TABLE admission_tickets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exchange_code_id BIGINT NOT NULL UNIQUE,
    member_id BIGINT,
    qr_token VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE admission_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admission_ticket_id BIGINT NOT NULL,
    staff_member_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    gate_name VARCHAR(100),
    processed_at TIMESTAMPTZ NOT NULL
);

-- =====================================================================
-- 8. 관심 부스·예약
-- =====================================================================

CREATE TABLE booth_interests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    booth_id BIGINT NOT NULL,
    vacancy_notification_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_interests UNIQUE (member_id, booth_id)
);

CREATE TABLE booth_reservation_slots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL,
    reserved_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_reservation_slots UNIQUE (booth_id, start_at),
    CONSTRAINT ck_booth_reservation_slots_capacity CHECK (capacity >= 0),
    CONSTRAINT ck_booth_reservation_slots_reserved_count CHECK (reserved_count BETWEEN 0 AND capacity)
);

CREATE TABLE booth_reservations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    booth_reservation_slot_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    party_size INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    checked_in_at TIMESTAMPTZ,
    no_show_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_reservations UNIQUE (member_id, booth_id),
    CONSTRAINT ck_booth_reservations_party_size CHECK (party_size > 0)
);

-- =====================================================================
-- 9. 부스 QR·리뷰·후기
-- =====================================================================

CREATE TABLE booth_qr_scans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    exchange_code_id BIGINT NOT NULL,
    scanned_at TIMESTAMPTZ NOT NULL,
    duplicate BOOLEAN NOT NULL
);

CREATE TABLE booth_reviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL,
    comment VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_reviews UNIQUE (member_id, booth_id),
    CONSTRAINT ck_booth_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

-- =====================================================================
-- 10. 알림
-- =====================================================================

CREATE TABLE notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(30),
    reference_id BIGINT,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

-- =====================================================================
-- 11. 광고
-- =====================================================================

CREATE TABLE advertisements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT,
    booth_id BIGINT,
    applicant_organization_id BIGINT NOT NULL,
    payment_order_id BIGINT,
    banner_file_id BIGINT,
    ad_text VARCHAR(300),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    reviewed_by BIGINT,
    rejection_reason TEXT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_advertisements_target CHECK (
        (event_id IS NOT NULL AND booth_id IS NULL)
     OR (event_id IS NULL AND booth_id IS NOT NULL)
    ),
    CONSTRAINT ck_advertisements_payment CHECK (
        booth_id IS NULL OR payment_order_id IS NULL
    )
);

-- =====================================================================
-- 12. 통계
-- =====================================================================

CREATE TABLE booth_hourly_statistics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    stat_hour SMALLINT NOT NULL,
    reservation_count INTEGER NOT NULL,
    no_show_count INTEGER NOT NULL,
    qr_scan_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_booth_hourly_statistics UNIQUE (booth_id, stat_date, stat_hour),
    CONSTRAINT ck_booth_hourly_statistics_stat_hour CHECK (stat_hour BETWEEN 0 AND 23),
    CONSTRAINT ck_booth_hourly_statistics_reservation_count CHECK (reservation_count >= 0),
    CONSTRAINT ck_booth_hourly_statistics_no_show_count CHECK (no_show_count >= 0),
    CONSTRAINT ck_booth_hourly_statistics_qr_scan_count CHECK (qr_scan_count >= 0)
);

-- =====================================================================
-- 13. 외래키 제약조건
-- =====================================================================

ALTER TABLE organization_members
    ADD CONSTRAINT fk_organization_members_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_organization_members_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE events
    ADD CONSTRAINT fk_events_organizer_organization FOREIGN KEY (organizer_organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_events_representative_file FOREIGN KEY (representative_file_id) REFERENCES file_assets (id);

ALTER TABLE event_members
    ADD CONSTRAINT fk_event_members_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_event_members_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE booth_recruitments
    ADD CONSTRAINT fk_booth_recruitments_event FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE booths
    ADD CONSTRAINT fk_booths_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_booths_assigned_organization FOREIGN KEY (assigned_organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_booths_representative_file FOREIGN KEY (representative_file_id) REFERENCES file_assets (id);

ALTER TABLE booth_applications
    ADD CONSTRAINT fk_booth_applications_recruitment FOREIGN KEY (recruitment_id) REFERENCES booth_recruitments (id),
    ADD CONSTRAINT fk_booth_applications_booth FOREIGN KEY (booth_id) REFERENCES booths (id),
    ADD CONSTRAINT fk_booth_applications_applicant_organization FOREIGN KEY (applicant_organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_booth_applications_applicant_member FOREIGN KEY (applicant_member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_booth_applications_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES members (id);

ALTER TABLE booth_application_files
    ADD CONSTRAINT fk_booth_application_files_application FOREIGN KEY (booth_application_id) REFERENCES booth_applications (id),
    ADD CONSTRAINT fk_booth_application_files_file FOREIGN KEY (file_id) REFERENCES file_assets (id);

ALTER TABLE venue_maps
    ADD CONSTRAINT fk_venue_maps_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_venue_maps_image_file FOREIGN KEY (image_file_id) REFERENCES file_assets (id);

ALTER TABLE booth_map_positions
    ADD CONSTRAINT fk_booth_map_positions_venue_map FOREIGN KEY (venue_map_id) REFERENCES venue_maps (id),
    ADD CONSTRAINT fk_booth_map_positions_booth FOREIGN KEY (booth_id) REFERENCES booths (id);

ALTER TABLE file_assets
    ADD CONSTRAINT fk_file_assets_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES members (id);

ALTER TABLE event_contents
    ADD CONSTRAINT fk_event_contents_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_event_contents_author_member FOREIGN KEY (author_member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_event_contents_file FOREIGN KEY (file_id) REFERENCES file_assets (id);

ALTER TABLE payment_orders
    ADD CONSTRAINT fk_payment_orders_buyer_member FOREIGN KEY (buyer_member_id) REFERENCES members (id);

ALTER TABLE ticket_orders
    ADD CONSTRAINT fk_ticket_orders_payment_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    ADD CONSTRAINT fk_ticket_orders_event FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_payment_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id);

ALTER TABLE payment_refunds
    ADD CONSTRAINT fk_payment_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    ADD CONSTRAINT fk_payment_refunds_exchange_code FOREIGN KEY (exchange_code_id) REFERENCES exchange_codes (id),
    ADD CONSTRAINT fk_payment_refunds_requester_member FOREIGN KEY (requester_member_id) REFERENCES members (id);

ALTER TABLE exchange_code_requests
    ADD CONSTRAINT fk_exchange_code_requests_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_exchange_code_requests_requested_by FOREIGN KEY (requested_by) REFERENCES members (id),
    ADD CONSTRAINT fk_exchange_code_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES members (id);

ALTER TABLE exchange_codes
    ADD CONSTRAINT fk_exchange_codes_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_exchange_codes_request FOREIGN KEY (exchange_code_request_id) REFERENCES exchange_code_requests (id),
    ADD CONSTRAINT fk_exchange_codes_ticket_order FOREIGN KEY (ticket_order_id) REFERENCES ticket_orders (id),
    ADD CONSTRAINT fk_exchange_codes_holder_member FOREIGN KEY (holder_member_id) REFERENCES members (id);

ALTER TABLE admission_tickets
    ADD CONSTRAINT fk_admission_tickets_exchange_code FOREIGN KEY (exchange_code_id) REFERENCES exchange_codes (id),
    ADD CONSTRAINT fk_admission_tickets_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE admission_logs
    ADD CONSTRAINT fk_admission_logs_ticket FOREIGN KEY (admission_ticket_id) REFERENCES admission_tickets (id),
    ADD CONSTRAINT fk_admission_logs_staff_member FOREIGN KEY (staff_member_id) REFERENCES members (id);

ALTER TABLE booth_interests
    ADD CONSTRAINT fk_booth_interests_member FOREIGN KEY (member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_booth_interests_booth FOREIGN KEY (booth_id) REFERENCES booths (id);

ALTER TABLE booth_reservation_slots
    ADD CONSTRAINT fk_booth_reservation_slots_booth FOREIGN KEY (booth_id) REFERENCES booths (id);

ALTER TABLE booth_reservations
    ADD CONSTRAINT fk_booth_reservations_booth FOREIGN KEY (booth_id) REFERENCES booths (id),
    ADD CONSTRAINT fk_booth_reservations_slot FOREIGN KEY (booth_reservation_slot_id) REFERENCES booth_reservation_slots (id),
    ADD CONSTRAINT fk_booth_reservations_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE booth_qr_scans
    ADD CONSTRAINT fk_booth_qr_scans_booth FOREIGN KEY (booth_id) REFERENCES booths (id),
    ADD CONSTRAINT fk_booth_qr_scans_exchange_code FOREIGN KEY (exchange_code_id) REFERENCES exchange_codes (id);

ALTER TABLE booth_reviews
    ADD CONSTRAINT fk_booth_reviews_booth FOREIGN KEY (booth_id) REFERENCES booths (id),
    ADD CONSTRAINT fk_booth_reviews_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_member FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE advertisements
    ADD CONSTRAINT fk_advertisements_event FOREIGN KEY (event_id) REFERENCES events (id),
    ADD CONSTRAINT fk_advertisements_booth FOREIGN KEY (booth_id) REFERENCES booths (id),
    ADD CONSTRAINT fk_advertisements_applicant_organization FOREIGN KEY (applicant_organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_advertisements_payment_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    ADD CONSTRAINT fk_advertisements_banner_file FOREIGN KEY (banner_file_id) REFERENCES file_assets (id),
    ADD CONSTRAINT fk_advertisements_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES members (id);

ALTER TABLE booth_hourly_statistics
    ADD CONSTRAINT fk_booth_hourly_statistics_booth FOREIGN KEY (booth_id) REFERENCES booths (id);

-- =====================================================================
-- 14. 인덱스
-- =====================================================================

CREATE INDEX idx_events_status_start_at ON events (status, start_at);
CREATE INDEX idx_events_organizer_organization_id ON events (organizer_organization_id);

CREATE INDEX idx_booths_event_id_status ON booths (event_id, status);

CREATE INDEX idx_booth_applications_booth_id_status ON booth_applications (booth_id, status);
CREATE INDEX idx_booth_applications_applicant_organization_status ON booth_applications (applicant_organization_id, status);

CREATE INDEX idx_exchange_codes_holder_member_status ON exchange_codes (holder_member_id, status);
CREATE INDEX idx_exchange_codes_event_status ON exchange_codes (event_id, status);
CREATE INDEX idx_exchange_codes_request_id ON exchange_codes (exchange_code_request_id);
CREATE INDEX idx_exchange_codes_ticket_order_id ON exchange_codes (ticket_order_id);

CREATE INDEX idx_admission_tickets_member_id ON admission_tickets (member_id);

CREATE INDEX idx_booth_reservations_slot_status ON booth_reservations (booth_reservation_slot_id, status);

CREATE INDEX idx_booth_qr_scans_booth_exchange_code_scanned_at ON booth_qr_scans (booth_id, exchange_code_id, scanned_at);
