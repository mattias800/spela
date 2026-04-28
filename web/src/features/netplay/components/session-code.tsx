import { useState } from "react";
import { Copy, Check } from "lucide-react";
import { Button } from "@/components/ui";
import { useToast } from "@/components/ui";

interface SessionCodeProps {
  code: string;
}

export function SessionCode({ code }: SessionCodeProps) {
  const [copied, setCopied] = useState(false);
  const { toast } = useToast();

  function handleCopy() {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true);
      toast("success", "Copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div data-comp="SessionCode" className="flex items-center gap-4">
      <div className="px-5 py-3 rounded-xl bg-surface-900 border border-surface-700">
        <span className="font-mono text-3xl tracking-[0.3em] text-surface-100">
          {code}
        </span>
      </div>
      <Button variant="secondary" size="sm" onClick={handleCopy}>
        {copied ? (
          <Check className="h-4 w-4" />
        ) : (
          <Copy className="h-4 w-4" />
        )}
        {copied ? "Copied!" : "Copy"}
      </Button>
    </div>
  );
}
