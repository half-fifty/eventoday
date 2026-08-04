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
import MyPage from "./pages/MyPage.jsx";
import OrganizerAdmin from "./pages/OrganizerAdmin.jsx";
import PlatformAdmin from "./pages/PlatformAdmin.jsx";
import EventDetail from "./pages/EventDetail.jsx";
import EventForm from "./pages/EventForm.jsx";
import EventMembers from "./pages/EventMembers.jsx";
import NotFound from "./pages/NotFound.jsx";
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
  { path: "/booth-detail", element: <BoothDetail /> },
  { path: "/booth-apply", element: <BoothApply /> },
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
      <NotificationSseProvider>
        <RouterProvider router={router} />
      </NotificationSseProvider>
    </AuthProvider>
  </React.StrictMode>
);
