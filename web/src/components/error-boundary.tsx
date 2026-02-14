import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui";

interface ErrorBoundaryProps {
  children: ReactNode;
  /** Optional fallback to render instead of the default error screen. */
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught:", error, errorInfo);
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="min-h-screen flex items-center justify-center bg-surface-950 px-4">
          <div className="max-w-md w-full text-center space-y-6">
            <div className="mx-auto h-16 w-16 rounded-2xl bg-danger-500/10 flex items-center justify-center">
              <AlertTriangle className="h-8 w-8 text-danger-500" />
            </div>
            <div className="space-y-2">
              <h1 className="text-2xl font-bold text-surface-100">
                Something went wrong
              </h1>
              <p className="text-surface-400 text-sm leading-relaxed">
                An unexpected error occurred. You can try refreshing the page or
                going back to the dashboard.
              </p>
            </div>
            {this.state.error && (
              <div className="rounded-xl bg-surface-900 border border-surface-800 p-4 text-left">
                <p className="text-xs font-mono text-surface-400 break-all">
                  {this.state.error.message}
                </p>
              </div>
            )}
            <div className="flex items-center justify-center gap-3">
              <Button variant="primary" size="sm" onClick={this.handleRetry}>
                <RefreshCw className="h-4 w-4" />
                Try Again
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  window.location.href = "/";
                }}
              >
                Go to Dashboard
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
