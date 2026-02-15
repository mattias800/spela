import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/hooks/use-auth";
import { ToastProvider } from "@/components/ui";
import { AppLayout } from "@/components/app-layout";
import { ProtectedRoute } from "@/components/protected-route";
import { LoginPage } from "@/pages/login-page";
import { RegisterPage } from "@/pages/register-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { ConsolesPage } from "@/pages/consoles-page";
import { ConsoleDetailPage } from "@/pages/console-detail-page";
import { GamesPage } from "@/pages/games-page";
import { GameDetailPage } from "@/pages/game-detail-page";
import { FavoritesPage } from "@/pages/favorites-page";
import { PlayLaterPage } from "@/pages/play-later-page";
import { AdminUsersPage } from "@/pages/admin/users-page";
import { AdminSettingsPage } from "@/pages/admin/settings-page";
import { AdminScanPage } from "@/pages/admin/scan-page";
import { MetadataFixPage } from "@/pages/admin/metadata-fix-page";
import { PreferencesPage } from "@/pages/preferences-page";
import { PlayPage } from "@/pages/play-page";
import { StatsPage } from "@/pages/stats-page";
import { ActivityPage } from "@/pages/activity-page";
import { CollectionsPage } from "@/pages/collections-page";
import { CollectionDetailPage } from "@/pages/collection-detail-page";
import { UserProfilePage } from "@/pages/user-profile-page";
import { RelaysPage } from "@/pages/relays-page";
import { RelayDetailPage } from "@/pages/relay-detail-page";
import { SetupPage } from "@/pages/setup-page";
import { LibraryLayout } from "@/components/library-layout";
import { ErrorBoundary } from "@/components/error-boundary";
import { useTheme } from "@/hooks/use-theme";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 0,
      retry: 1,
    },
  },
});

function ThemeApplier({ children }: { children: React.ReactNode }) {
  useTheme();
  return <>{children}</>;
}

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <ToastProvider>
              <ThemeApplier>
                <Routes>
                  {/* Auth routes */}
                  <Route path="/setup" element={<SetupPage />} />
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />

                  {/* Emulator route (protected, no sidebar) */}
                  <Route
                    path="games/:id/play"
                    element={
                      <ProtectedRoute>
                        <PlayPage />
                      </ProtectedRoute>
                    }
                  />

                  {/* App routes (protected) */}
                  <Route
                    element={
                      <ProtectedRoute>
                        <AppLayout />
                      </ProtectedRoute>
                    }
                  >
                    <Route index element={<DashboardPage />} />
                    <Route element={<LibraryLayout />}>
                      <Route path="consoles" element={<ConsolesPage />} />
                      <Route path="games" element={<GamesPage />} />
                      <Route path="favorites" element={<FavoritesPage />} />
                      <Route path="play-later" element={<PlayLaterPage />} />
                      <Route path="collections" element={<CollectionsPage />} />
                    </Route>
                    <Route
                      path="consoles/:id"
                      element={<ConsoleDetailPage />}
                    />
                    <Route path="games/:id" element={<GameDetailPage />} />
                    <Route
                      path="collections/:id"
                      element={<CollectionDetailPage />}
                    />
                    <Route path="stats" element={<StatsPage />} />
                    <Route path="activity" element={<ActivityPage />} />
                    <Route path="relays" element={<RelaysPage />} />
                    <Route path="relays/:id" element={<RelayDetailPage />} />
                    <Route path="users/:id" element={<UserProfilePage />} />
                    <Route path="preferences" element={<PreferencesPage />} />

                    {/* Admin routes */}
                    <Route
                      path="admin/users"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminUsersPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/settings"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminSettingsPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/scan"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminScanPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/metadata"
                      element={
                        <ProtectedRoute requireAdmin>
                          <MetadataFixPage />
                        </ProtectedRoute>
                      }
                    />
                  </Route>

                  {/* Fallback */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </ThemeApplier>
            </ToastProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
