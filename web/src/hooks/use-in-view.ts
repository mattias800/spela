import { useCallback, useRef, useState } from "react";

interface UseInViewOptions {
  rootMargin?: string;
}

export function useInView({ rootMargin = "200px" }: UseInViewOptions = {}) {
  const [isInView, setIsInView] = useState(false);
  const observerRef = useRef<IntersectionObserver | null>(null);

  const observe = useCallback(
    (node: HTMLElement | null) => {
      if (observerRef.current) {
        observerRef.current.disconnect();
        observerRef.current = null;
      }

      if (!node) return;

      observerRef.current = new IntersectionObserver(
        (entries) => {
          if (entries[0]?.isIntersecting) {
            setIsInView(true);
            observerRef.current?.disconnect();
            observerRef.current = null;
          }
        },
        { rootMargin },
      );

      observerRef.current.observe(node);
    },
    [rootMargin],
  );

  return { observe, isInView };
}
