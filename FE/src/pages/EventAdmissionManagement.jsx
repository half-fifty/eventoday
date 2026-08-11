import { Link, useParams } from "react-router-dom";
import AdmissionManagementPanel from "../components/AdmissionManagementPanel.jsx";
import TopNav from "../components/TopNav.jsx";

export default function EventAdmissionManagement() {
  const { eventId } = useParams();

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="organizer" />
      <main className="mx-auto max-w-[1200px] px-lg py-xl">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">ADMISSION</p>
            <h1 className="font-display-lg text-[30px]">현장 입장 관리</h1>
          </div>
          <Link to="/organizer-admin" className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
            관리자 홈
          </Link>
        </div>
        <AdmissionManagementPanel eventId={eventId} />
      </main>
    </div>
  );
}
