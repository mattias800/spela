import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/hooks/use-auth";
import { ToastProvider } from "@/components/ui";
import { AppLayout } from "@/components/app-layout";
import { ProtectedRoute } from "@/components/protected-route";
import { LoginPage } from "@/pages/login-page";
import { RegisterPage } from "@/pages/register-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { ConsolesPage } from "@/pages/consoles-page";
import { ConsoleDetailPage } from "@/pages/console-detail-page";
import { ConsoleGamesPage } from "@/pages/console-games-page";
import { GamesPage } from "@/pages/games-page";
import { GameDetailPage } from "@/pages/game-detail-page";
import { GameAchievementsPage } from "@/pages/game-achievements-page";
import { FavoritesPage } from "@/pages/favorites-page";
import { PlayLaterPage } from "@/pages/play-later-page";
import { AdminUsersPage } from "@/pages/admin/users-page";
import { AdminSettingsPage } from "@/pages/admin/settings-page";
import { AdminScanPage } from "@/pages/admin/scan-page";
import { MetadataFixPage } from "@/pages/admin/metadata-fix-page";
import { AdminCheatsPage } from "@/pages/admin/cheats-page";
import { AdminBiosPage } from "@/pages/admin/bios-page";
import { CoreCompatibilityPage } from "@/pages/admin/core-compatibility-page";
import { AdminSecurityEventsPage } from "@/pages/admin/security-events-page";
import { UploadRomsPage } from "@/pages/admin/upload-roms-page";
import { RomHacksPage } from "@/pages/admin/rom-hacks-page";
import { PreferencesPage } from "@/pages/preferences-page";
import { PlayPage } from "@/pages/play-page";
import { StatsPage } from "@/pages/stats-page";
import { ActivityPage } from "@/pages/activity-page";
import { CollectionsPage } from "@/pages/collections-page";
import { CollectionDetailPage } from "@/pages/collection-detail-page";
import { UserProfilePage } from "@/pages/user-profile-page";
import { SharedSessionsPage } from "@/pages/shared-sessions-page";
import { SharedSessionDetailPage } from "@/pages/shared-session-detail-page";
import { NetplayPage } from "@/pages/netplay-page";
import { NetplaySessionPage } from "@/pages/netplay-session-page";
import { LicensesPage } from "@/pages/licenses-page";
import { ChallengesPage } from "@/pages/challenges-page";
import { TopListsPage } from "@/pages/top-lists-page";
import { ExplorePage } from "@/pages/explore-page";
import { ExploreThemePage } from "@/pages/explore-theme-page";
import { ExploreKeywordPage } from "@/pages/explore-keyword-page";
import { ExploreSeriesPage } from "@/pages/explore-series-page";
import { ExploreFranchisePage } from "@/pages/explore-franchise-page";
import { DeveloperDetailPage } from "@/pages/developer-detail-page";
import { PublisherDetailPage } from "@/pages/publisher-detail-page";
import { ExploreMoodPage } from "@/pages/explore-mood-page";
import { ExploreWizardPage } from "@/pages/explore-wizard-page";
import { ScreenshotGalleryPage } from "@/pages/screenshot-gallery-page";
import { CoverGalleryPage } from "@/pages/cover-gallery-page";
import { ChallengeDetailPage } from "@/pages/challenge-detail-page";
import { SessionDetailPage } from "@/pages/session-detail-page";
import { SetupWizardPage } from "@/pages/setup-wizard-page";
import { StoragePage } from "@/pages/storage-page";
import { LibraryLayout } from "@/components/library-layout";
import { ErrorBoundary } from "@/components/error-boundary";
import { useTheme } from "@/hooks/use-theme";

import { queryClient } from "@/lib/query-client";

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
                      path="admin/security-events"
                      element={
                        <ProtectedRoute requireAdmin>
                          <AdminSecurityEventsPage />
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
