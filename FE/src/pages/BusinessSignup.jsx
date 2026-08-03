import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signupBusiness, verifyBusiness } from "../api/businessAuthApi.js";

const inputClassName =
  "h-[48px] w-full rounded-xl border border-hairline bg-white px-md text-on-surface outline-none placeholder:text-ink-muted focus:border-primary-focus focus:ring-1 focus:ring-primary-focus disabled:cursor-not-allowed disabled:bg-surface-container disabled:text-secondary";

export default function BusinessSignup() {
  const navigate = useNavigate();
  const [organizationType, setOrganizationType] = useState("ORGANIZER");
  const [businessNumber, setBusinessNumber] = useState("");
  const [startDate, setStartDate] = useState("");
  const [representativeName, setRepresentativeName] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [verificationStatus, setVerificationStatus] = useState("idle");
  const [verificationMessage, setVerificationMessage] = useState("");
  const isBusinessVerified = verificationStatus === "success";

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
        setVerificationMessage("사업자 인증이 완료됐습니다.");
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
      setVerificationMessage(error.message || "사업자 인증 중 오류가 발생했습니다.");
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (verificationStatus !== "success") {
      window.alert("사업자 인증을 먼저 완료해 주세요.");
      return;
    }

    if (password !== passwordConfirm) {
      window.alert("비밀번호가 일치하지 않습니다.");
      return;
    }

    setIsSubmitting(true);

    try {
      await signupBusiness({
        organizationType,
        businessNumber,
        startDate,
        representativeName: representativeName.trim(),
        organizationName: organizationName.trim(),
        contactEmail: contactEmail.trim(),
        contactPhone: contactPhone.trim(),
        password,
      });

      window.alert("사업자 회원가입이 완료됐습니다.");
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
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
        <Link to="/login" className="text-caption text-white/70 hover:text-white">로그인</Link>
      </header>

      <main className="mx-auto w-full max-w-[720px] px-lg py-xxl">
        <div className="mb-xl">
          <p className="mb-xs text-caption font-semibold text-primary-focus">사업자 전용</p>
          <h1 className="mb-sm text-[32px] font-semibold tracking-tight">사업자 회원가입</h1>
          <p className="text-caption text-secondary">사업자 정보를 인증하고 회사 공용 계정을 만들어 주세요.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-xl">
          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-md text-body font-semibold">가입 유형</h2>
            <div className="grid gap-sm sm:grid-cols-2">
              {[
                { value: "ORGANIZER", title: "박람회 개최측", description: "행사를 등록하고 부스를 모집합니다." },
                { value: "EXHIBITOR", title: "부스 참가측", description: "모집 중인 부스에 참가를 신청합니다." },
              ].map((type) => (
                <button
                  key={type.value}
                  type="button"
                  onClick={() => setOrganizationType(type.value)}
                  className={`rounded-xl border p-md text-left transition-colors ${organizationType === type.value ? "border-primary-focus bg-primary-fixed" : "border-hairline hover:border-outline-variant"}`}
                >
                  <span className="block font-semibold">{type.title}</span>
                  <span className="mt-xxs block text-[12px] text-secondary">{type.description}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-2xl border border-hairline bg-white p-xl shadow-sm">
            <h2 className="mb-xs text-body font-semibold">사업자 인증</h2>
            <p className="mb-lg text-[12px] text-secondary">사업자등록증에 기재된 정보를 입력해 주세요.</p>
            <div className="grid gap-md sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <span className="mb-xs block text-caption font-semibold">사업자등록번호</span>
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
                        ? "인증 완료"
                        : "인증하기"}
                  </button>
                </div>
              </label>
              <label className="block">
                <span className="mb-xs block text-caption font-semibold">개업일자</span>
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
                <span className="mb-xs block text-caption font-semibold">대표자명</span>
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
            <h2 className="mb-lg text-body font-semibold">회사 및 로그인 정보</h2>
            <div className="grid gap-md sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <span className="mb-xs block text-caption font-semibold">회사명</span>
                <input name="organizationName" type="text" value={organizationName} onChange={(event) => setOrganizationName(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <span className="mb-xs block text-caption font-semibold">회사 이메일</span>
                <input name="contactEmail" type="email" autoComplete="email" value={contactEmail} onChange={(event) => setContactEmail(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <span className="mb-xs block text-caption font-semibold">회사 전화번호</span>
                <input name="contactPhone" type="tel" autoComplete="tel" value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <span className="mb-xs block text-caption font-semibold">비밀번호</span>
                <input name="password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} required className={inputClassName} />
              </label>
              <label className="block">
                <span className="mb-xs block text-caption font-semibold">비밀번호 확인</span>
                <input name="passwordConfirm" type="password" autoComplete="new-password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} required className={inputClassName} />
              </label>
            </div>
          </section>

          <button type="submit" disabled={isSubmitting} className="h-[52px] w-full rounded-xl bg-primary text-button-large font-semibold text-white transition-colors hover:bg-primary-focus disabled:cursor-not-allowed disabled:opacity-50">
            {isSubmitting ? "가입 중" : "사업자 회원가입"}
          </button>
        </form>
      </main>
    </div>
  );
}
