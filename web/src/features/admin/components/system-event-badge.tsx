import { Badge } from "@/components/ui";
import type { SystemEventTypeLike } from "@/types/api";
import {
  getSystemEventLabel,
  getSystemEventMeta,
  UNKNOWN_EVENT_META,
} from "./system-event-meta";

interface SystemEventBadgeProps {
  type: SystemEventTypeLike;
}

export function SystemEventBadge({ type }: SystemEventBadgeProps) {
  const meta = getSystemEventMeta(type) ?? UNKNOWN_EVENT_META;
  const Icon = meta.icon;
  const label = getSystemEventLabel(type);
  return (
    <Badge variant={meta.variant}>
      <Icon aria-hidden="true" className="h-3 w-3 mr-1" />
      {label}
    </Badge>
  );
}
