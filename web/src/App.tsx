import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/hooks/use-auth";
import { ToastProvider } from "@/components/ui";
import { AppLayout } from "@/components/app-layout";
import { ProtectedRoute } from "@/components/protected-route";
import { LibraryLayout } from "@/components/library-layout";
import { ErrorBoundary } from "@/components/error-boundary";
import { useTheme } from "@/hooks/use-theme";

// Lazy-loaded pages — one chunk per screen via the router. Each page uses
// `export default`; tests still use the named export. See feedback memory
// "Code-split per screen via lazy router imports" for the convention.
const LoginPage = lazy(() => import("@/pages/login-page"));
const RegisterPage = lazy(() => import("@/pages/register-page"));
const DashboardPage = lazy(() => import("@/pages/dashboard-page"));
const ConsolesPage = lazy(() => import("@/pages/consoles-page"));
const ConsoleDetailPage = lazy(() => import("@/pages/console-detail-page"));
const ConsoleGamesPage = lazy(() => import("@/pages/console-games-page"));
const GamesPage = lazy(() => import("@/pages/games-page"));
const GameDetailPage = lazy(() => import("@/pages/game-detail-page"));
const GameAchievementsPage = lazy(() => import("@/pages/game-achievements-page"));
const FavoritesPage = lazy(() => import("@/pages/favorites-page"));
const PlayLaterPage = lazy(() => import("@/pages/play-later-page"));
const AdminUsersPage = lazy(() => import("@/pages/admin/users-page"));
const AdminSettingsPage = lazy(() => import("@/pages/admin/settings-page"));
const AdminScanPage = lazy(() => import("@/pages/admin/scan-page"));
const MetadataFixPage = lazy(() => import("@/pages/admin/metadata-fix-page"));
const AdminCheatsPage = lazy(() => import("@/pages/admin/cheats-page"));
const AdminBiosPage = lazy(() => import("@/pages/admin/bios-page"));
const CoreCompatibilityPage = lazy(() => import("@/pages/admin/core-compatibility-page"));
const AdminSystemEventsPage = lazy(() => import("@/pages/admin/system-events-page"));
const UploadRomsPage = lazy(() => import("@/pages/admin/upload-roms-page"));
const RomHacksPage = lazy(() => import("@/pages/admin/rom-hacks-page"));
const PreferencesPage = lazy(() => import("@/pages/preferences-page"));
const PlayPage = lazy(() => import("@/pages/play-page"));
const StatsPage = lazy(() => import("@/pages/stats-page"));
const ActivityPage = lazy(() => import("@/pages/activity-page"));
const CollectionsPage = lazy(() => import("@/pages/collections-page"));
const CollectionDetailPage = lazy(() => import("@/pages/collection-detail-page"));
const UserProfilePage = lazy(() => import("@/pages/user-profile-page"));
const SharedSessionsPage = lazy(() => import("@/pages/shared-sessions-page"));
const SharedSessionDetailPage = lazy(() => import("@/pages/shared-session-detail-page"));
const NetplayPage = lazy(() => import("@/pages/netplay-page"));
const NetplaySessionPage = lazy(() => import("@/pages/netplay-session-page"));
const LicensesPage = lazy(() => import("@/pages/licenses-page"));
const ChallengesPage = lazy(() => import("@/pages/challenges-page"));
const TopListsPage = lazy(() => import("@/pages/top-lists-page"));
const ExplorePage = lazy(() => import("@/pages/explore-page"));
const ExploreThemePage = lazy(() => import("@/pages/explore-theme-page"));
const ExploreKeywordPage = lazy(() => import("@/pages/explore-keyword-page"));
const ExploreSeriesPage = lazy(() => import("@/pages/explore-series-page"));
const ExploreFranchisePage = lazy(() => import("@/pages/explore-franchise-page"));
const DeveloperDetailPage = lazy(() => import("@/pages/developer-detail-page"));
const PublisherDetailPage = lazy(() => import("@/pages/publisher-detail-page"));
const ExploreMoodPage = lazy(() => import("@/pages/explore-mood-page"));
const ExploreWizardPage = lazy(() => import("@/pages/explore-wizard-page"));
const ScreenshotGalleryPage = lazy(() => import("@/pages/screenshot-gallery-page"));
const CoverGalleryPage = lazy(() => import("@/pages/cover-gallery-page"));
const ChallengeDetailPage = lazy(() => import("@/pages/challenge-detail-page"));
const SessionDetailPage = lazy(() => import("@/pages/session-detail-page"));
const SetupWizardPage = lazy(() => import("@/pages/setup-wizard-page"));
const StoragePage = lazy(() => import("@/pages/storage-page"));

import { queryClient } from "@/lib/query-client";

function ThemeApplier({ children }: { children: React.ReactNode }) {
  useTheme();
  return <>{children}</>;
}

// Minimal full-screen fallback shown while a lazy-loaded route chunk is
// downloading. Intentionally subtle — most chunks load in <100ms on a warm
// connection, so a heavy spinner would flash and feel worse than nothing.
function RouteFallback() {
  return (
    <div
      data-testid="route-fallback"
      className="min-h-screen bg-surface-950"
      aria-hidden="true"
    />
  );
}

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <ToastProvider>
              <ThemeApplier>
                <Suspense fallback={<RouteFallback />}>
                <Routes>
                  {/* Auth routes */}
                  <Route path="/setup" element={<SetupWizardPage />} />
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />

                  {/* Emulator route (protected, no sidebar) */}
                  <Route
                    path="games/:id/play/:sessionId"
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
                    <Route path="explore" element={<ExplorePage />} />
                    <Route
                      path="explore/themes/:id"
                      element={<ExploreThemePage />}
                    />
                    <Route
                      path="explore/keywords/:id"
                      element={<ExploreKeywordPage />}
                    />
                    <Route
                      path="explore/series/:id"
                      element={<ExploreSeriesPage />}
                    />
                    <Route
                      path="explore/franchise/:id"
                      element={<ExploreFranchisePage />}
                    />
                    <Route
                      path="explore/gallery"
                      element={<ScreenshotGalleryPage />}
                    />
                    <Route
                      path="explore/covers"
                      element={<CoverGalleryPage />}
                    />
                    <Route
                      path="explore/mood/:mood"
                      element={<ExploreMoodPage />}
                    />
                    <Route
                      path="explore/wizard"
                      element={<ExploreWizardPage />}
                    />
                    <Route
                      path="explore/developers/:name"
                      element={<DeveloperDetailPage />}
                    />
                    <Route
                      path="explore/publishers/:name"
                      element={<PublisherDetailPage />}
                    />
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
                    <Route
                      path="consoles/:id/games"
                      element={<ConsoleGamesPage />}
                    />
                    <Route path="games/:id" element={<GameDetailPage />} />
                    <Route
                      path="games/:id/achievements"
                      element={<GameAchievementsPage />}
                    />
                    <Route
                      path="sessions/:sessionId"
                      element={<SessionDetailPage />}
                    />
                    <Route
                      path="collections/:id"
                      element={<CollectionDetailPage />}
                    />
                    <Route path="stats" element={<StatsPage />} />
                    <Route path="activity" element={<ActivityPage />} />
                    <Route path="top-lists" element={<TopListsPage />} />
                    <Route path="challenges" element={<ChallengesPage />} />
                    <Route
                      path="challenges/:id"
                      element={<ChallengeDetailPage />}
                    />
                    <Route path="shared-sessions" element={<SharedSessionsPage />} />
                    <Route path="shared-sessions/:id" element={<SharedSessionDetailPage />} />
                    <Route path="netplay" element={<NetplayPage />} />
                    <Route path="netplay/:id" element={<NetplaySessionPage />} />
                    <Route path="users/:id" element={<UserProfilePage />} />
                    <Route path="preferences" element={<PreferencesPage />} />
                    <Route path="storage" element={<StoragePage />} />
                    <Route path="licenses" element={<LicensesPage />} />

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
                      path="admin/bios"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminBiosPage />
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
                      path="admin/upload"
                      element={
                        <ProtectedRoute requireAdmin>
                          <UploadRomsPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/rom-hacks"
                      element={
                        <ProtectedRoute requireAdmin>
                          <RomHacksPage />
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
                    <Route
                      path="admin/cheats"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminCheatsPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/core-compatibility"
                      element={
                        <ProtectedRoute requireAdmin>
                          <CoreCompatibilityPage />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="admin/system-events"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminSystemEventsPage />
                        </ProtectedRoute>
                      }
                    />
                  </Route>

                  {/* Fallback */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
                </Suspense>
              </ThemeApplier>
            </ToastProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
