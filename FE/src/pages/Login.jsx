import { Link, Navigate, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import { loginWithGoogle } from "../api/authApi.js";
import useAuth from "../hooks/useAuth.js";

const reasonMsgs = {
  interest: "관심 부스 등록은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
  reserve: "부스 예약은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
};

export default function Login() {
  const [params] = useSearchParams();
  const { loading, isAuthenticated } = useAuth();
  const reason = params.get("reason");
  const contextMsg =
    (reason && reasonMsgs[reason]) || "소셜 계정으로 로그인하고 회원 전용 기능을 이용하세요.";

  if (loading) {
    return (
      <div className="min-h-screen bg-black text-white flex items-center justify-center">
        로그인 상태를 확인하고 있습니다.
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/mypage" replace />;
  }

  return (
    <div className="bg-black text-white min-h-screen flex flex-col">
      <header className="h-[44px] flex items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
      </header>

      <main className="flex-1 flex flex-col items-center justify-center px-lg py-xxl">
        <div className="w-full max-w-[380px]">
          <div className="text-center mb-xxl">
            <Icon name="confirmation_number" className="text-[40px] text-primary-on-dark mb-md block" />
            <h1 className="font-hero-display text-[28px] font-semibold mb-sm">간편하게 시작하기</h1>
            <p className="text-white/60 text-caption">{contextMsg}</p>
          </div>

          <div className="space-y-sm">
            <button disabled className="w-full h-[52px] rounded-xl bg-[#FEE500] text-black font-body-strong flex items-center justify-center gap-sm opacity-40 cursor-not-allowed">
              <Icon name="chat_bubble" className="text-[20px]" /> 카카오 로그인 준비 중
            </button>
            <button disabled className="w-full h-[52px] rounded-xl bg-[#03C75A] text-white font-body-strong flex items-center justify-center gap-sm opacity-40 cursor-not-allowed">
              <Icon name="language" className="text-[20px]" /> 네이버 로그인 준비 중
            </button>
            <button
              type="button"
              onClick={loginWithGoogle}
              className="group relative h-10 w-full min-w-min max-w-[400px] select-none appearance-none overflow-hidden whitespace-nowrap rounded-[20px] border border-[#747775] bg-white p-0 text-center align-middle [font-family:Roboto,Arial,sans-serif] text-[14px] tracking-[0.25px] text-[#1f1f1f] outline-none transition-[background-color,border-color,box-shadow] duration-[218ms] hover:shadow-[0_1px_2px_0_rgba(60,64,67,0.30),0_1px_3px_1px_rgba(60,64,67,0.15)] focus-visible:ring-2 focus-visible:ring-[#1a73e8] focus-visible:ring-offset-2 active:scale-[0.99] disabled:cursor-default disabled:border-[#1f1f1f1f] disabled:bg-[#ffffff61]"
            >
              <span className="absolute inset-0 opacity-0 transition-opacity duration-[218ms] group-hover:bg-[#303030] group-hover:opacity-[0.08] group-focus:bg-[#303030] group-focus:opacity-[0.12] group-active:bg-[#303030] group-active:opacity-[0.12]" />
              <span className="relative flex h-full w-full flex-row flex-nowrap items-center justify-between">
                <span className="h-10 w-10 min-w-10 p-[9px]">
                  <svg
                    aria-hidden="true"
                    viewBox="0 0 18 18"
                    className="h-5 w-5"
                  >
                    <path fill="#EA4335" d="M17.64 9.205c0-.638-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.797 2.715v2.258h2.909c1.702-1.567 2.684-3.875 2.684-6.613Z" />
                    <path fill="#4285F4" d="M9 18c2.43 0 4.467-.806 5.956-2.182l-2.909-2.258c-.806.54-1.835.859-3.047.859-2.344 0-4.328-1.585-5.037-3.714H.956v2.333A9 9 0 0 0 9 18Z" />
                    <path fill="#FBBC05" d="M3.963 10.705A5.413 5.413 0 0 1 3.682 9c0-.592.102-1.167.281-1.705V4.962H.956A9 9 0 0 0 0 9c0 1.452.347 2.827.956 4.038l3.007-2.333Z" />
                    <path fill="#34A853" d="M9 3.58c1.322 0 2.508.454 3.441 1.346l2.582-2.582C13.463.892 11.426 0 9 0A9 9 0 0 0 .956 4.962l3.007 2.333C4.672 5.166 6.656 3.58 9 3.58Z" />
                  </svg>
                </span>
                <span className="grow overflow-hidden text-ellipsis font-medium align-top">
                  Google로 계속하기
                </span>
                <span className="h-10 w-10 min-w-10" aria-hidden="true" />
              </span>
            </button>
          </div>

          <p className="text-center text-[11px] text-white/40 mt-lg leading-relaxed">
            계속 진행 시 EXPO HUB의 <a href="#" className="underline">이용약관</a> 및 <a href="#" className="underline">개인정보처리방침</a>에 동의하는 것으로 간주됩니다.
          </p>

          <div className="border-t border-white/10 mt-xxl pt-xl text-center">
            <p className="text-caption text-white/60 mb-sm">비회원으로 발급받은 티켓이 있으신가요?</p>
            <p className="text-[12px] text-white/40">로그인하면 기존 비회원 티켓이 자동으로 회원 계정에 연결돼요.</p>
          </div>
        </div>
      </main>

    </div>
  );
}
