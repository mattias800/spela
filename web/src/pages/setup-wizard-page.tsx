import { useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { WizardLayout } from "@/features/setup/components/wizard-layout";
import { SystemCheckStep } from "@/features/setup/components/system-check-step";
import { CreateAccountStep } from "@/features/setup/components/create-account-step";
import { IgdbStep } from "@/features/setup/components/igdb-step";
import { GameScanStep } from "@/features/setup/components/game-scan-step";
import { CompleteStep } from "@/features/setup/components/complete-step";

export function SetupWizardPage() {
  const { needsSetup, isLoading, isAuthenticated } = useAuth();
  const [currentStep, setCurrentStep] = useState(0);
  const [igdbConfigured, setIgdbConfigured] = useState(false);
  const [gamesScanned, setGamesScanned] = useState(false);

  if (isLoading) return null;
  if (needsSetup === false && !isAuthenticated)
    return <Navigate to="/login" replace />;

  function nextStep() {
    setCurrentStep((prev) => prev + 1);
  }

  return (
    <WizardLayout currentStep={currentStep}>
      {currentStep === 0 && <SystemCheckStep onNext={nextStep} />}
      {currentStep === 1 && <CreateAccountStep onNext={nextStep} />}
      {currentStep === 2 && (
        <IgdbStep
          onSkip={nextStep}
          onSave={() => {
            setIgdbConfigured(true);
            nextStep();
          }}
        />
      )}
      {currentStep === 3 && (
        <GameScanStep
          onSkip={nextStep}
          onComplete={() => {
            setGamesScanned(true);
            nextStep();
          }}
        />
      )}
      {currentStep === 4 && (
        <CompleteStep
          igdbConfigured={igdbConfigured}
          gamesScanned={gamesScanned}
        />
      )}
    </WizardLayout>
  );
}
