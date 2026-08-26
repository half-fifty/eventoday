import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signupBusiness, verifyBusiness } from "../api/businessAuthApi.js";

const inputClassName =
  "h-[48px] w-full rounded-xl border border-hairline bg-white px-md text-on-surface outline-none placeholder:text-ink-muted focus:border-primary-focus focus:ring-1 focus:ring-primary-focus disabled:cursor-not-allowed disabled:bg-surface-container disabled:text-secondary";

const FieldLabel = ({ children, required }) => (
  <span className="mb-xs flex items-center gap-1 text-caption font-semibold">
    <span>{children}</span>
    {required ? (
      <span className="text-error" aria-hidden="true">*</span>
    ) : (
      <span className="text-[11px] font-normal text-secondary">(선택)</span>
    )}
  </span>
);

const ORGANIZATION_TYPES = [
  {
    value: "EXHIBITOR",
    title: "부스 참가기업",
    description: "모집 중인 행사에 부스 참가를 신청하고 부스를 운영합니다.",
    hint: "가입 완료 후 바로 이용할 수 있습니다.",
  },
  {
    value: "ORGANIZER",
    title: "행사 개최자",
    description: "행사를 등록하고 참가기업을 모집합니다.",
    hint: "플랫폼 관리자 승인 후 이용할 수 있습니다.",
  },
];

export default function BusinessSignup() {
  const navigate = useNavigate();
  const [organizationType, setOrganizationType] = useState("EXHIBITOR");
  const [businessNumber, setBusinessNumber] = useState("");
  const [startDate, setStartDate] = useState("");
  const [representativeName, setRepresentativeName] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [managerName, setManagerName] = useState("");
  const [managerPhone, setManagerPhone] = useState("");
  const [postalCode, setPostalCode] = useState("");
  const [addressLine1, setAddressLine1] = useState("");
  const [addressLine2, setAddressLine2] = useState("");
  const [homepageUrl, setHomepageUrl] = useState("");
  const [introduction, setIntroduction] = useState("");
  const [certificateFile, setCertificateFile] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [verificationStatus, setVerificationStatus] = useState("idle");
  const [verificationMessage, setVerificationMessage] = useState("");
  const isBusinessVerified = verificationStatus === "success";
  const isOrganizer = organizationType === "ORGANIZER";

  const resetVerification = () => {
    setVerificationStatus("idle");
    setVerificationMessage("");
  };

  const handleBusinessNumberChange = (event) => {
    setBusinessNumber(event.target.value.replace(/\D/g, "").slice(0, 10));
    resetVerification();
  };

  const handleStartDateChange = (event) => {
    setStartDate(event.target.value.replace(/\D/g, "").slice(0, 8));
    resetVerification();
  };

  const handleRepresentativeNameChange = (event) => {
    setRepresentativeName(event.target.value);
    resetVerification();
  };

  const handleVerification = async () => {
    if (
      businessNumber.length !== 10 ||
      startDate.length !== 8 ||
      representativeName.trim() === ""
    ) {
      setVerificationStatus("error");
      setVerificationMessage("사업자등록번호, 개업일자, 대표자명을 확인해 주세요.");
      return;
    }

    setVerificationStatus("loading");
    setVerificationMessage("");

    try {
      const result = await verifyBusiness({
        businessNumber,
        startDate,
        representativeName: representativeName.trim(),
      });

      if (result?.valid && result?.active) {
        setVerificationStatus("success");
        setVerificationMessage("사업자 확인이 완료됐습니다.");
        return;
      }

      setVerificationStatus("error");
      setVerificationMessage(
        result?.valid
          ? "현재 영업 중인 사업자가 아닙니다."
          : "입력한 사업자 정보를 확인할 수 없습니다."
      );
    } catch (error) {
      setVerificationStatus("error");
      setVerificationMessage(error.message || "사업자 확인 중 오류가 발생했습니다.");
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (verificationStatus !== "success") {
      window.alert("사업자 확인을 먼저 완료해 주세요.");
      return;
    }

    if (password !== passwordConfirm) {
      window.alert("비밀번호가 일치하지 않습니다.");
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await signupBusiness(
        {
          organizationType,
          businessNumber,
          startDate,
          representativeName: representativeName.trim(),
          organizationName: organizationName.trim(),
          contactEmail: contactEmail.trim(),
          contactPhone: contactPhone.trim(),
          email: email.trim(),
          password,
          managerName: managerName.trim(),
          managerPhone: managerPhone.trim(),
          postalCode: postalCode.trim(),
          addressLine1: addressLine1.trim(),
          addressLine2: addressLine2.trim(),
          homepageUrl: homepageUrl.trim(),
          introduction: introduction.trim(),
        },
        isOrganizer ? certificateFile : null
      );

      if (response?.organizationStatus === "PENDING") {
        window.alert(
          "가입 신청이 접수되었습니다. 플랫폼 관리자 승인 후 로그인할 수 있습니다."
        );
      } else {
        window.alert(
          "참가기업 회원가입이 완료되었습니다. 로그인 후 모집 중인 행사에 참가 신청할 수 있습니다."
        );
      }

      navigate("/login", { replace: true });
    } catch (error) {
      window.alert(error.message || "사업자 회원가입 중 오류가 발생했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface text-on-surface">
      <header className="h-[52px] border-b border-hairline bg-black px-lg flex items-center justify-between">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <Link to="/login" className="text-caption text-white/70 hover:text-white">로그인</Link>
      </header>

      <main className="mx-auto w-full max-w-[720px] px-lg py-xxl">
        <div className="mb-xl">
          <p className="mb-xs text-caption font-semibold text-primary-focus">사업자 전용</p>
          <h1 className="mb-sm text-[32px] font-semibold tracking-tight">사업자 회원가입</h1>
          <p className="text-caption text-secondary">사업자 정보를 확인하고 회사 공용 계정을 만들어 주세요.</p>
          <p className="mt-xs text-[11px] text-secondary"><span className="text-error">*</span> 표시는 필수 입력 항목이며, (선택)이 붙은 항목은 입력하지 않아도 가입할 수 있습니다.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-xl">
          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-md text-body font-semibold">가입 유형</h2>
            <div className="grid gap-sm sm:grid-cols-2">
              {ORGANIZATION_TYPES.map((type) => (
                <button
                  key={type.value}
                  type="button"
                  onClick={() => setOrganizationType(type.value)}
                  className={`rounded-xl border p-md text-left transition-colors ${organizationType === type.value ? "border-primary-focus bg-primary-fixed" : "border-hairline hover:border-outline-variant"}`}
                >
                  <span className="block font-semibold">{type.title}</span>
                  <span className="mt-xxs block text-[12px] text-secondary">{type.description}</span>
                  <span className="mt-xxs block text-[11px] text-primary-focus">{type.hint}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-lg text-body font-semibold">계정 정보</h2>
            <div className="grid gap-md sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <FieldLabel required>로그인 이메일</FieldLabel>
                <input name="email" type="text" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>비밀번호</FieldLabel>
                <input name="password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>비밀번호 확인</FieldLabel>
                <input name="passwordConfirm" type="password" autoComplete="new-password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>담당자 이름</FieldLabel>
                <input name="managerName" type="text" value={managerName} onChange={(event) => setManagerName(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>담당자 연락처</FieldLabel>
                <input name="managerPhone" type="tel" value={managerPhone} onChange={(event) => setManagerPhone(event.target.value)} required className={inputClassName} />
              </label>
            </div>
          </section>

          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-xs text-body font-semibold">사업자 확인</h2>
            <p className="mb-lg text-[12px] text-secondary">사업자등록증에 기재된 정보를 입력해 주세요.</p>
            <div className="grid gap-md sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <FieldLabel required>사업자등록번호</FieldLabel>
                <div className="flex gap-xs">
                  <input
                    name="businessNumber"
                    inputMode="numeric"
                    placeholder="숫자 10자리"
                    value={businessNumber}
                    onChange={handleBusinessNumberChange}
                    disabled={isBusinessVerified}
                    required
                    className={inputClassName}
                  />
                  <button
                    type="button"
                    onClick={handleVerification}
                    disabled={verificationStatus === "loading" || isBusinessVerified}
                    className="shrink-0 rounded-xl bg-on-primary-fixed px-lg text-caption font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {verificationStatus === "loading"
                      ? "확인 중"
                      : isBusinessVerified
                        ? "확인 완료"
                        : "사업자 확인"}
                  </button>
                </div>
              </label>
              <label className="block">
                <FieldLabel required>개업일자</FieldLabel>
                <input
                  name="startDate"
                  type="text"
                  inputMode="numeric"
                  placeholder="YYYYMMDD"
                  value={startDate}
                  onChange={handleStartDateChange}
                  disabled={isBusinessVerified}
                  required
                  className={inputClassName}
                />
              </label>
              <label className="block">
                <FieldLabel required>대표자명</FieldLabel>
                <input
                  name="representativeName"
                  type="text"
                  placeholder="홍길동"
                  value={representativeName}
                  onChange={handleRepresentativeNameChange}
                  disabled={isBusinessVerified}
                  required
                  className={inputClassName}
                />
              </label>
            </div>
            {verificationMessage && (
              <p className={`mt-md text-[12px] ${verificationStatus === "success" ? "text-status-available" : "text-error"}`}>
                {verificationMessage}
              </p>
            )}
          </section>

          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-lg text-body font-semibold">회사 정보</h2>
            <div className="grid gap-md sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <FieldLabel required>상호·법인명</FieldLabel>
                <input name="organizationName" type="text" value={organizationName} onChange={(event) => setOrganizationName(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>회사 대표 이메일</FieldLabel>
                <input name="contactEmail" type="email" autoComplete="email" value={contactEmail} onChange={(event) => setContactEmail(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <FieldLabel required>회사 대표 전화번호</FieldLabel>
                <input name="contactPhone" type="tel" autoComplete="tel" value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block sm:col-span-2">
                <FieldLabel>우편번호</FieldLabel>
                <input name="postalCode" type="text" inputMode="numeric" value={postalCode} onChange={(event) => setPostalCode(event.target.value)} className={inputClassName} />
              </label>
              <label className="block sm:col-span-2">
                <FieldLabel>기본 주소</FieldLabel>
                <input name="addressLine1" type="text" value={addressLine1} onChange={(event) => setAddressLine1(event.target.value)} className={inputClassName} />
              </label>
              <label className="block sm:col-span-2">
                <FieldLabel>상세 주소</FieldLabel>
                <input name="addressLine2" type="text" value={addressLine2} onChange={(event) => setAddressLine2(event.target.value)} className={inputClassName} />
              </label>
              <label className="block sm:col-span-2">
                <FieldLabel>홈페이지</FieldLabel>
                <input name="homepageUrl" type="url" placeholder="https://" value={homepageUrl} onChange={(event) => setHomepageUrl(event.target.value)} className={inputClassName} />
              </label>
              <label className="block sm:col-span-2">
                <FieldLabel>회사 소개</FieldLabel>
                <textarea name="introduction" rows={3} value={introduction} onChange={(event) => setIntroduction(event.target.value)} className={`${inputClassName} h-auto py-sm`} />
              </label>
            </div>
          </section>

          {isOrganizer && (
            <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
              <h2 className="mb-xs flex items-center gap-1 text-body font-semibold">
                사업자등록증
                <span className="text-[11px] font-normal text-secondary">(선택)</span>
              </h2>
              <p className="mb-md text-[12px] text-secondary">플랫폼 관리자 심사에 사용됩니다.</p>
              <input
                name="certificateFile"
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp,application/pdf,.doc,.docx"
                onChange={(event) => setCertificateFile(event.target.files?.[0] || null)}
                className="block w-full text-caption"
              />
            </section>
          )}

          <button type="submit" disabled={isSubmitting} className="h-[52px] w-full rounded-xl bg-primary text-button-large font-semibold text-white transition-colors hover:bg-primary-focus disabled:cursor-not-allowed disabled:opacity-50">
            {isSubmitting ? "가입 중" : isOrganizer ? "개최자 가입 신청" : "참가기업 가입 완료"}
          </button>
        </form>
      </main>
    </div>
  );
}
