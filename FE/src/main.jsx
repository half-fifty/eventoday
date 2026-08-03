import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import "./index.css";

import Home from "./pages/Home.jsx";
import Login from "./pages/Login.jsx";
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
import { AuthProvider } from "./auth/AuthProvider.jsx";
import ProtectedRoute from "./auth/ProtectedRoute.jsx";

const router = createBrowserRouter([
  { path: "/", element: <Home /> },
  { path: "/login", element: <Login /> },
  { path: "/event-ongoing", element: <EventOngoing /> },
  { path: "/event-recruiting", element: <EventRecruiting /> },
  { path: "/recruitments", element: <RecruitmentList /> },
  { path: "/recruitments/:recruitmentId", element: <RecruitmentDetail /> },
  { path: "/recruitment-check", element: <RecruitmentCheck /> },
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
  {
    path: "/platform-admin",
    element: (
      <ProtectedRoute roles={["PLATFORM_ADMIN"]}>
        <PlatformAdmin />
      </ProtectedRoute>
    ),
  },
]);

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </React.StrictMode>
);
