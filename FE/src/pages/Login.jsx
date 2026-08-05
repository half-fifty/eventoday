import { useEffect, useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import { loginWithGoogle, loginWithKakao, loginWithNaver } from "../api/authApi.js";
import { loginBusiness } from "../api/businessAuthApi.js";
import useAuth from "../hooks/useAuth.js";

const reasonMsgs = {
  interest: "관심 부스 등록은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
  reserve: "부스 예약은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
};

const POST_LOGIN_REDIRECT_KEY = "postLoginRedirect";

const getSafeRedirect = (redirect) => {
  if (
    typeof redirect === "string" &&
    redirect.startsWith("/") &&
    !redirect.startsWith("//")
  ) {
    return redirect;
  }

  return null;
};

export default function Login() {
  const [params] = useSearchParams();
  const { loading, isAuthenticated, refreshMember } = useAuth();
  const [loginType, setLoginType] = useState("social");
  const [businessNumber, setBusinessNumber] = useState("");
  const [password, setPassword] = useState("");
  const [businessLoginError, setBusinessLoginError] = useState("");
  const [isBusinessLoginLoading, setIsBusinessLoginLoading] = useState(false);
  const reason = params.get("reason");
  const requestedRedirect = getSafeRedirect(
    params.get("redirect")
  );
  const storedRedirect = getSafeRedirect(
    sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY)
  );
  const redirectPath =
    requestedRedirect || storedRedirect || "/";
  const contextMsg =
    (reason && reasonMsgs[reason]) || "소셜 계정으로 로그인하고 회원 전용 기능을 이용하세요.";

  useEffect(() => {
    if (isAuthenticated) {
      sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
    }
  }, [isAuthenticated]);

  const handleGoogleLogin = () => {
    sessionStorage.setItem(
      POST_LOGIN_REDIRECT_KEY,
      redirectPath
    );
    loginWithGoogle();
  };

  const handleNaverLogin = () => {
    sessionStorage.setItem(
      POST_LOGIN_REDIRECT_KEY,
      redirectPath
    );
    loginWithNaver();
  };

  const handleKakaoLogin = () => {
    sessionStorage.setItem(
      POST_LOGIN_REDIRECT_KEY,
      redirectPath
    );
    loginWithKakao();
  };

  const handleBusinessLogin = async (event) => {
    event.preventDefault();
    setBusinessLoginError("");
    setIsBusinessLoginLoading(true);

    try {
      await loginBusiness({ businessNumber, password });
    } catch (error) {
      setBusinessLoginError(
        error.message || "사업자 로그인에 실패했습니다."
      );
      setIsBusinessLoginLoading(false);
      return;
    }

    let currentMember = null;

    try {
      currentMember = await refreshMember();
    } catch {
      currentMember = null;
    } finally {
      setIsBusinessLoginLoading(false);
    }

    if (currentMember !== null) {
      return;
    }

    setBusinessLoginError(
      "로그인은 완료됐지만 회원 정보를 불러오지 못했습니다. 화면을 새로고침해 주세요."
    );
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-black text-white flex items-center justify-center">
        로그인 상태를 확인하고 있습니다.
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to={redirectPath} replace />;
  }

  return (
    <div className="bg-black text-white min-h-screen flex flex-col">
      <header className="h-[44px] flex items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
      </header>

      <main className="flex-1 flex flex-col items-center justify-center px-lg py-xxl">
        <div className="w-full max-w-[420px]">
          <div className="text-center mb-xl">
            <Icon name="confirmation_number" className="text-[40px] text-primary-on-dark mb-md block" />
            <h1 className="font-hero-display text-[28px] font-semibold mb-sm">로그인</h1>
            <p className="text-white/60 text-caption">{contextMsg}</p>
          </div>

          <div className="mb-lg grid grid-cols-2 rounded-xl bg-white/10 p-1">
            <button
              type="button"
              onClick={() => setLoginType("social")}
              className={`h-10 rounded-lg text-caption font-semibold transition-colors ${loginType === "social" ? "bg-white text-black" : "text-white/60 hover:text-white"}`}
            >
              일반 사용자
            </button>
            <button
              type="button"
              onClick={() => setLoginType("business")}
              className={`h-10 rounded-lg text-caption font-semibold transition-colors ${loginType === "business" ? "bg-white text-black" : "text-white/60 hover:text-white"}`}
            >
              사업자
            </button>
          </div>

          {loginType === "social" ? (
            <div>
              <p className="mb-md text-center text-caption text-white/60">일반 회원은 소셜 계정으로 로그인해 주세요.</p>
              <div className="space-y-sm">
                <button
                  type="button"
                  onClick={handleGoogleLogin}
                  className="group relative h-10 w-full select-none appearance-none overflow-hidden whitespace-nowrap rounded-[20px] border border-[#747775] bg-white p-0 text-center align-middle [font-family:Roboto,Arial,sans-serif] text-[14px] tracking-[0.25px] text-[#1f1f1f] outline-none transition-[background-color,border-color,box-shadow] duration-[218ms] hover:shadow-[0_1px_2px_0_rgba(60,64,67,0.30),0_1px_3px_1px_rgba(60,64,67,0.15)] focus-visible:ring-2 focus-visible:ring-[#1a73e8]"
                >
                  <span className="absolute inset-0 opacity-0 transition-opacity duration-[218ms] group-hover:bg-[#303030] group-hover:opacity-[0.08]" />
                  <span className="relative flex h-full w-full items-center justify-between">
                    <span className="h-10 w-10 p-[9px]">
                      <svg aria-hidden="true" viewBox="0 0 18 18" className="h-5 w-5">
                        <path fill="#EA4335" d="M17.64 9.205c0-.638-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.797 2.715v2.258h2.909c1.702-1.567 2.684-3.875 2.684-6.613Z" />
                        <path fill="#4285F4" d="M9 18c2.43 0 4.467-.806 5.956-2.182l-2.909-2.258c-.806.54-1.835.859-3.047.859-2.344 0-4.328-1.585-5.037-3.714H.956v2.333A9 9 0 0 0 9 18Z" />
                        <path fill="#FBBC05" d="M3.963 10.705A5.413 5.413 0 0 1 3.682 9c0-.592.102-1.167.281-1.705V4.962H.956A9 9 0 0 0 0 9c0 1.452.347 2.827.956 4.038l3.007-2.333Z" />
                        <path fill="#34A853" d="M9 3.58c1.322 0 2.508.454 3.441 1.346l2.582-2.582C13.463.892 11.426 0 9 0A9 9 0 0 0 .956 4.962l3.007 2.333C4.672 5.166 6.656 3.58 9 3.58Z" />
                      </svg>
                    </span>
                    <span className="grow overflow-hidden text-ellipsis font-medium">Google로 계속하기</span>
                    <span className="h-10 w-10" aria-hidden="true" />
                  </span>
                </button>

                <button
                  type="button"
                  onClick={handleNaverLogin}
                  className="flex h-10 w-full items-center rounded-[20px] bg-[#03C75A] text-[14px] font-semibold text-white transition-colors hover:bg-[#02b351] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#03C75A] focus-visible:ring-offset-2 focus-visible:ring-offset-black"
                >
                  <span className="flex h-10 w-10 items-center justify-center text-[18px] font-black" aria-hidden="true">N</span>
                  <span className="grow pr-10 text-center">네이버로 계속하기</span>
                </button>

                <button
                  type="button"
                  onClick={handleKakaoLogin}
                  className="flex h-10 w-full items-center rounded-[20px] bg-[#FEE500] text-[14px] font-semibold text-black/85 transition-colors hover:bg-[#f5dc00] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FEE500] focus-visible:ring-offset-2 focus-visible:ring-offset-black"
                >
                  <span className="flex h-10 w-10 items-center justify-center" aria-hidden="true">
                    <svg viewBox="0 0 24 24" className="h-5 w-5 fill-black/85">
                      <path d="M12 3C6.477 3 2 6.477 2 10.765c0 2.736 1.82 5.143 4.57 6.526l-1.16 4.255a.36.36 0 0 0 .548.392l4.99-3.302c.345.035.696.053 1.052.053 5.523 0 10-3.477 10-7.764S17.523 3 12 3Z" />
                    </svg>
                  </span>
                  <span className="grow pr-10 text-center">카카오로 계속하기</span>
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleBusinessLogin} className="space-y-md">
              <label className="block">
                <span className="mb-xs block text-caption text-white/70">사업자등록번호</span>
                <input
                  type="text"
                  inputMode="numeric"
                  autoComplete="username"
                  value={businessNumber}
                  onChange={(event) => setBusinessNumber(event.target.value.replace(/\D/g, "").slice(0, 10))}
                  placeholder="숫자 10자리"
                  required
                  className="h-[48px] w-full rounded-xl border border-white/20 bg-white/10 px-md text-white outline-none placeholder:text-white/30 focus:border-primary-on-dark"
                />
              </label>
              <label className="block">
                <span className="mb-xs block text-caption text-white/70">비밀번호</span>
                <input
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="비밀번호 입력"
                  required
                  className="h-[48px] w-full rounded-xl border border-white/20 bg-white/10 px-md text-white outline-none placeholder:text-white/30 focus:border-primary-on-dark"
                />
              </label>
              {businessLoginError && (
                <p className="text-[12px] text-error">{businessLoginError}</p>
              )}
              <button type="submit" disabled={isBusinessLoginLoading} className="h-[48px] w-full rounded-xl bg-primary-container font-semibold text-white transition-colors hover:bg-primary-focus disabled:cursor-not-allowed disabled:opacity-50">
                {isBusinessLoginLoading ? "로그인 중" : "사업자 로그인"}
              </button>
              <p className="text-center text-caption text-white/50">
                처음 이용하시나요?{" "}
                <Link to="/business/signup" className="font-semibold text-primary-on-dark hover:underline">사업자 회원가입</Link>
              </p>
            </form>
          )}

          <p className="mt-lg text-center text-[11px] leading-relaxed text-white/40">
            계속 진행 시 EXPO HUB의 <a href="#" className="underline">이용약관</a> 및 <a href="#" className="underline">개인정보처리방침</a>에 동의하는 것으로 간주됩니다.
          </p>
        </div>
      </main>

    </div>
  );
}
