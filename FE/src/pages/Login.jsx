import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";

const reasonMsgs = {
  interest: "관심 부스 등록은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
  reserve: "부스 예약은 회원 전용 기능이에요. 로그인 후 계속 진행할 수 있어요.",
};
const labels = { kakao: "카카오", naver: "네이버", google: "Google" };

export default function Login() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirect = params.get("redirect");
  const reason = params.get("reason");
  const contextMsg =
    (reason && reasonMsgs[reason]) || "소셜 계정으로 로그인하고 회원 전용 기능을 이용하세요.";

  const [toast, setToast] = useState(null);

  const socialLogin = (provider) => {
    setToast(`${labels[provider]} 계정으로 로그인되었습니다`);
    setTimeout(() => {
      navigate(redirect || "/mypage");
    }, 900);
  };

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
            <button onClick={() => socialLogin("kakao")} className="w-full h-[52px] rounded-xl bg-[#FEE500] text-black font-body-strong flex items-center justify-center gap-sm active:scale-95 transition-transform">
              <Icon name="chat_bubble" className="text-[20px]" /> 카카오로 계속하기
            </button>
            <button onClick={() => socialLogin("naver")} className="w-full h-[52px] rounded-xl bg-[#03C75A] text-white font-body-strong flex items-center justify-center gap-sm active:scale-95 transition-transform">
              <Icon name="language" className="text-[20px]" /> 네이버로 계속하기
            </button>
            <button onClick={() => socialLogin("google")} className="w-full h-[52px] rounded-xl bg-white text-black font-body-strong flex items-center justify-center gap-sm active:scale-95 transition-transform border border-hairline">
              <Icon name="g_mobiledata" className="text-[20px]" /> Google로 계속하기
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

      {toast && (
        <div className="fixed bottom-xl left-1/2 -translate-x-1/2 bg-white text-black px-lg py-md rounded-full font-body-strong text-caption shadow-xl flex items-center gap-sm">
          <Icon name="check_circle" className="text-status-available text-[18px]" />
          <span>{toast}</span>
        </div>
      )}
    </div>
  );
}
