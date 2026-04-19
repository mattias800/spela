import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { api, typedApi, unwrap, ApiError } from "@/lib/api-client";
import type { User, AuthTokens } from "@/types/api";

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  needsSetup: boolean | null;
  registrationEnabled: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (
    username: string,
    email: string,
    password: string,
  ) => Promise<{ pending: boolean }>;
  setup: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null);
  const [registrationEnabled, setRegistrationEnabled] = useState(true);

  useEffect(() => {
    // Fetch profile with one retry for transient failures (network blip,
    // server busy).  Only clear tokens on definitive auth failures (401/403).
    const fetchProfile = (retriesLeft: number): void => {
      unwrap(typedApi.GET("/api/user/profile"))
        .then((u) => {
          setUser(u as User);
          setIsLoading(false);
        })
        .catch((err: unknown) => {
          const status =
            err instanceof ApiError
              ? err.status
              : err instanceof Error && "status" in err
                ? (err as Error & { status: number }).status
                : 0;
          if (status === 401 || status === 403) {
            api.clearTokens();
            setIsLoading(false);
            return;
          }
          if (retriesLeft > 0) {
            setTimeout(() => fetchProfile(retriesLeft - 1), 500);
            return;
          }
          setIsLoading(false);
        });
    };

    // Check setup status first
    unwrap(typedApi.GET("/api/auth/setup-status"))
      .then((data) => {
        const d = data as { needsSetup: boolean; registrationEnabled: boolean };
        setNeedsSetup(d.needsSetup);
        setRegistrationEnabled(d.registrationEnabled);

        if (d.needsSetup) {
          setIsLoading(false);
          return;
        }

        // Only check token if setup is complete
        const token = api.getAccessToken();
        if (!token) {
          setIsLoading(false);
          return;
        }
        fetchProfile(1);
      })
      .catch(() => {
        setNeedsSetup(false);
        setIsLoading(false);
      });
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data = (await unwrap(
      typedApi.POST("/api/auth/login", { body: { username, password } }),
    )) as AuthTokens;
    api.setTokens(data.accessToken, data.refreshToken);
    setUser(data.user);
  }, []);

  const register = useCallback(
    async (
      username: string,
      email: string,
      password: string,
    ): Promise<{ pending: boolean }> => {
      const data = (await unwrap(
        typedApi.POST("/api/auth/register", {
          body: { username, email, password },
        }),
      )) as AuthTokens | { pending: true; message: string };
      if ("pending" in data && data.pending) {
        return { pending: true };
      }
      const tokens = data as AuthTokens;
      api.setTokens(tokens.accessToken, tokens.refreshToken);
      setUser(tokens.user);
      return { pending: false };
    },
    [],
  );

  const setup = useCallback(
    async (username: string, email: string, password: string) => {
      const data = (await unwrap(
        typedApi.POST("/api/auth/setup", {
          body: { username, email, password },
        }),
      )) as AuthTokens;
      api.setTokens(data.accessToken, data.refreshToken);
      setUser(data.user);
      setNeedsSetup(false);
    },
    [],
  );

  const logout = useCallback(() => {
    api.clearTokens();
    setUser(null);
  }, []);

  return (
    <AuthContext
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        isAdmin: user?.role === "admin" || user?.role === "owner",
        needsSetup,
        registrationEnabled,
        login,
        register,
        setup,
        logout,
      }}
    >
      {children}
    </AuthContext>
  );
}
