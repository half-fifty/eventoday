import React, { Suspense, lazy } from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import "./index.css";

import Home from "./pages/Home.jsx";
const Login = lazy(() => import("./pages/Login.jsx"));
const BusinessSignup = lazy(() => import("./pages/BusinessSignup.jsx"));
const EventOngoing = lazy(() => import("./pages/EventOngoing.jsx"));
const EventRecruiting = lazy(() => import("./pages/EventRecruiting.jsx"));
const RecruitmentList = lazy(() => import("./pages/RecruitmentList.jsx"));
const RecruitmentDetail = lazy(() => import("./pages/RecruitmentDetail.jsx"));
const RecruitmentCheck = lazy(() => import("./pages/RecruitmentCheck.jsx"));
const BoothDetail = lazy(() => import("./pages/BoothDetail.jsx"));
const BoothApply = lazy(() => import("./pages/BoothApply.jsx"));
const BoothApplicationList = lazy(() => import("./pages/BoothApplicationList.jsx"));
const MyPage = lazy(() => import("./pages/MyPage.jsx"));
const OrganizerAdmin = lazy(() => import("./pages/OrganizerAdmin.jsx"));
const ExhibitorAdmin = lazy(() => import("./pages/ExhibitorAdmin.jsx"));
const PlatformAdmin = lazy(() => import("./pages/PlatformAdmin.jsx"));
const EventDetail = lazy(() => import("./pages/EventDetail.jsx"));
const EventList = lazy(() => import("./pages/EventList.jsx"));
const EventForm = lazy(() => import("./pages/EventForm.jsx"));
const EventMembers = lazy(() => import("./pages/EventMembers.jsx"));
const OrganizerAdvertisements = lazy(() => import("./pages/OrganizerAdvertisements.jsx"));
const AdvertisementPaymentResult = lazy(() => import("./pages/AdvertisementPaymentResult.jsx"));
const TicketPaymentResult = lazy(() => import("./pages/TicketPaymentResult.jsx"));
const Notices = lazy(() => import("./pages/Notices.jsx"));
const NoticeDetail = lazy(() => import("./pages/NoticeDetail.jsx"));
const TicketOrderDetail = lazy(() => import("./pages/TicketOrderDetail.jsx"));
const PaymentDetail = lazy(() => import("./pages/PaymentDetail.jsx"));
const RefundDetail = lazy(() => import("./pages/RefundDetail.jsx"));
const RefundPolicy = lazy(() => import("./pages/RefundPolicy.jsx"));
const AdmissionTicketDetail = lazy(() => import("./pages/AdmissionTicketDetail.jsx"));
const EventAdmissionManagement = lazy(() => import("./pages/EventAdmissionManagement.jsx"));
const StaffAdmissionEvents = lazy(() => import("./pages/StaffAdmissionEvents.jsx"));
const GuestOrderAccess = lazy(() => import("./pages/GuestOrderAccess.jsx"));
const GuestReservationManagement = lazy(() => import("./pages/GuestReservationManagement.jsx"));
const NotFound = lazy(() => import("./pages/NotFound.jsx"));
const VenueGuide = lazy(() => import("./pages/VenueGuide.jsx"));
import { AuthProvider } from "./auth/AuthProvider.jsx";
import ProtectedRoute from "./auth/ProtectedRoute.jsx";
import NotificationSseProvider from "./notifications/NotificationSseProvider.jsx";
import ToastProvider from "./feedback/ToastProvider.jsx";
import AlertModalProvider from "./feedback/AlertModalProvider.jsx";

const organizerOnly = (element) => (
  <ProtectedRoute roles={["USER"]} organizationTypes={["ORGANIZER"]}>
    {element}
  </ProtectedRoute>
);

const router = createBrowserRouter([
  { path: "/", element: <Home /> },
  { path: "/login", element: <Login /> },
  { path: "/business/signup", element: <BusinessSignup /> },
  { path: "/events/:eventId/ongoing", element: <EventOngoing /> },
  { path: "/event-recruiting", element: <EventRecruiting /> },
  { path: "/recruitments", element: <RecruitmentList /> },
  { path: "/recruitments/:recruitmentId", element: <RecruitmentDetail /> },
  { path: "/recruitment-check", element: <RecruitmentCheck /> },
  { path: "/events/:eventId", element: <EventDetail /> },
  { path: "/events", element: <EventList /> },
  { path: "/venues", element: <VenueGuide /> },
  { path: "/notices", element: <Notices /> },
  { path: "/notices/:noticeId", element: <NoticeDetail /> },
  { path: "/booth-detail", element: <BoothDetail /> },
  { path: "/booth-apply", element: <BoothApply /> },
  {
    path: "/my-applications",
    element: (
      <ProtectedRoute>
        <BoothApplicationList />
      </ProtectedRoute>
    ),
  },
  {
    path: "/mypage",
    element: (
      <ProtectedRoute>
        <MyPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/organizer-admin",
    element: (
      <ProtectedRoute roles={["USER"]} organizationTypes={["ORGANIZER"]}>
        <OrganizerAdmin />
      </ProtectedRoute>
    ),
  },
  {
    path: "/exhibitor-admin",
    element: (
      <ProtectedRoute roles={["USER"]} organizationTypes={["EXHIBITOR"]}>
        <ExhibitorAdmin />
      </ProtectedRoute>
    ),
  },
  { path: "/organizer-admin/events/new", element: organizerOnly(<EventForm />) },
  { path: "/organizer-admin/events/:eventId/edit", element: organizerOnly(<EventForm />) },
  { path: "/organizer-admin/events/:eventId/members", element: organizerOnly(<EventMembers />) },
  { path: "/organizer-admin/advertisements", element: organizerOnly(<OrganizerAdvertisements />) },
  { path: "/organizer-admin/advertisements/payment/success", element: organizerOnly(<AdvertisementPaymentResult />) },
  { path: "/organizer-admin/advertisements/payment/fail", element: organizerOnly(<AdvertisementPaymentResult failed />) },
  { path: "/tickets/payment/success", element: <TicketPaymentResult /> },
  { path: "/tickets/payment/fail", element: <TicketPaymentResult failed /> },
  { path: "/tickets/orders/:orderNo", element: <TicketOrderDetail /> },
  { path: "/payments/:paymentId", element: <PaymentDetail /> },
  { path: "/refunds/:refundId", element: <RefundDetail /> },
  { path: "/refund-policy", element: <RefundPolicy /> },
  { path: "/admission-tickets/:admissionTicketId", element: <AdmissionTicketDetail /> },
  { path: "/guest/orders", element: <GuestOrderAccess /> },
  { path: "/guest/orders/:orderNo", element: <GuestReservationManagement /> },
  {
    path: "/events/:eventId/admission",
    element: (
      <ProtectedRoute>
        <EventAdmissionManagement />
      </ProtectedRoute>
    ),
  },
  {
    path: "/staff/admission",
    element: (
      <ProtectedRoute>
        <StaffAdmissionEvents />
      </ProtectedRoute>
    ),
  },
  {
    path: "/platform-admin",
    element: (
      <ProtectedRoute roles={["PLATFORM_ADMIN"]}>
        <PlatformAdmin />
      </ProtectedRoute>
    ),
  },
  { path: "*", element: <NotFound /> },
]);

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AuthProvider>
      <ToastProvider>
        <AlertModalProvider>
          <NotificationSseProvider onNavigate={(path) => router.navigate(path)}>
            <Suspense fallback={<main className="grid min-h-screen place-items-center bg-surface text-ink-muted">페이지를 불러오는 중입니다.</main>}>
              <RouterProvider router={router} />
            </Suspense>
          </NotificationSseProvider>
        </AlertModalProvider>
      </ToastProvider>
    </AuthProvider>
  </React.StrictMode>
);
