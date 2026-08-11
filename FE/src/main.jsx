import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import "./index.css";

import Home from "./pages/Home.jsx";
import Login from "./pages/Login.jsx";
import BusinessSignup from "./pages/BusinessSignup.jsx";
import EventOngoing from "./pages/EventOngoing.jsx";
import EventRecruiting from "./pages/EventRecruiting.jsx";
import RecruitmentList from "./pages/RecruitmentList.jsx";
import RecruitmentDetail from "./pages/RecruitmentDetail.jsx";
import RecruitmentCheck from "./pages/RecruitmentCheck.jsx";
import BoothDetail from "./pages/BoothDetail.jsx";
import BoothApply from "./pages/BoothApply.jsx";
import BoothApplicationList from "./pages/BoothApplicationList.jsx";
import MyPage from "./pages/MyPage.jsx";
import OrganizerAdmin from "./pages/OrganizerAdmin.jsx";
import PlatformAdmin from "./pages/PlatformAdmin.jsx";
import EventDetail from "./pages/EventDetail.jsx";
import EventList from "./pages/EventList.jsx";
import EventForm from "./pages/EventForm.jsx";
import EventMembers from "./pages/EventMembers.jsx";
import OrganizerAdvertisements from "./pages/OrganizerAdvertisements.jsx";
import AdvertisementPaymentResult from "./pages/AdvertisementPaymentResult.jsx";
import TicketPaymentResult from "./pages/TicketPaymentResult.jsx";
import Notices from "./pages/Notices.jsx";
import TicketOrderDetail from "./pages/TicketOrderDetail.jsx";
import PaymentDetail from "./pages/PaymentDetail.jsx";
import RefundDetail from "./pages/RefundDetail.jsx";
import AdmissionTicketDetail from "./pages/AdmissionTicketDetail.jsx";
import EventAdmissionManagement from "./pages/EventAdmissionManagement.jsx";
import NotFound from "./pages/NotFound.jsx";
import VenueGuide from "./pages/VenueGuide.jsx";
import { AuthProvider } from "./auth/AuthProvider.jsx";
import ProtectedRoute from "./auth/ProtectedRoute.jsx";
import NotificationSseProvider from "./notifications/NotificationSseProvider.jsx";

const router = createBrowserRouter([
  { path: "/", element: <Home /> },
  { path: "/login", element: <Login /> },
  { path: "/business/signup", element: <BusinessSignup /> },
  { path: "/event-ongoing", element: <EventOngoing /> },
  { path: "/event-recruiting", element: <EventRecruiting /> },
  { path: "/recruitments", element: <RecruitmentList /> },
  { path: "/recruitments/:recruitmentId", element: <RecruitmentDetail /> },
  { path: "/recruitment-check", element: <RecruitmentCheck /> },
  { path: "/events/:eventId", element: <EventDetail /> },
  { path: "/events", element: <EventList /> },
  { path: "/venues", element: <VenueGuide /> },
  { path: "/notices", element: <Notices /> },
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
  { path: "/organizer-admin", element: <OrganizerAdmin /> },
  { path: "/organizer-admin/events/new", element: <EventForm /> },
  { path: "/organizer-admin/events/:eventId/edit", element: <EventForm /> },
  { path: "/organizer-admin/events/:eventId/members", element: <EventMembers /> },
  { path: "/organizer-admin/advertisements", element: <OrganizerAdvertisements /> },
  { path: "/organizer-admin/advertisements/payment/success", element: <AdvertisementPaymentResult /> },
  { path: "/organizer-admin/advertisements/payment/fail", element: <AdvertisementPaymentResult failed /> },
  { path: "/tickets/payment/success", element: <TicketPaymentResult /> },
  { path: "/tickets/payment/fail", element: <TicketPaymentResult failed /> },
  { path: "/tickets/orders/:orderNo", element: <TicketOrderDetail /> },
  { path: "/payments/:paymentId", element: <PaymentDetail /> },
  { path: "/refunds/:refundId", element: <RefundDetail /> },
  { path: "/admission-tickets/:admissionTicketId", element: <AdmissionTicketDetail /> },
  {
    path: "/events/:eventId/admission",
    element: (
      <ProtectedRoute>
        <EventAdmissionManagement />
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
      <NotificationSseProvider onNavigate={(path) => router.navigate(path)}>
        <RouterProvider router={router} />
      </NotificationSseProvider>
    </AuthProvider>
  </React.StrictMode>
);
