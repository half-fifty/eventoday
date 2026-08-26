import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

// 방문→행사상세→티켓선택→결제완료 4단계를 가로 막대그래프로 보여준다.
// 개최자센터/관리자센터 양쪽의 행사 상세 퍼널 화면에서 공용으로 쓴다.
const FUNNEL_STEPS = [
  { key: "totalSessions", label: "방문" },
  { key: "viewEventDetailCount", label: "행사상세 조회" },
  { key: "openPurchaseModalCount", label: "티켓선택" },
  { key: "completePaymentCount", label: "결제완료" },
];

export default function FunnelStepChart({ summary }) {
  const data = FUNNEL_STEPS.map(({ key, label }) => ({ label, count: Number(summary[key]) || 0 }));

  return (
    <div className="h-[260px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" allowDecimals={false} />
          <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value) => [Number(value).toLocaleString(), "인원"]} />
          <Bar dataKey="count" fill="#004e9f" radius={[0, 6, 6, 0]} barSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
