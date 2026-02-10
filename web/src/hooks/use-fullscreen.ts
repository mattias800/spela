import { useCallback, useEffect, type RefObject } from "react";

export function useFullscreen(iframeRef: RefObject<HTMLIFrameElement | null>) {
  const handleFullscreen = useCallback(() => {
    const iframe = iframeRef.current;
    if (iframe) {
      iframe.requestFullscreen?.().catch(() => {});
    }
  }, [iframeRef]);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "F11") {
        e.preventDefault();
        handleFullscreen();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [handleFullscreen]);

  return { handleFullscreen };
}
