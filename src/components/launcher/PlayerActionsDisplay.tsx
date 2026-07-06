"use client";

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/utils';
import { SkinView3DWrapper } from '../common/SkinView3DWrapper';
import { MainLaunchButton } from './MainLaunchButton';
import { useThemeStore } from '../../store/useThemeStore';
import { useSkinStore } from '../../store/useSkinStore';
import { MinecraftSkinService } from '../../services/minecraft-skin-service';
import { useMinecraftAuthStore } from '../../store/minecraft-auth-store';
import * as skinview3d from 'skinview3d';
import { Icon } from '@iconify/react';
import { ServerLaunchCard } from './ServerLaunchCard';
import { useProfileStore } from '../../store/profile-store';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { usePoseStore, POSES } from '../../store/usePoseStore';
import { usePlaytimeStore } from '../../store/usePlaytimeStore';

const DEFAULT_FALLBACK_SKIN_URL = "/skins/default_steve_full.png"; // Defined constant for fallback URL

// Featured server configuration
// Option A: profileId = null → uses currently selected profile from MainLaunchButton
// Option B: profileId = "uuid" → uses dedicated profile for this server
const FEATURED_SERVER = {
  address: "hugosmp.net",
  name: "HUGOSMP.net",
  profileId: null as string | null, // TODO: Set dedicated profile ID for Option B
};

interface PlayerActionsDisplayProps {
  playerName: string | null | undefined;
  launchButtonDefaultVersion: string;
  onLaunchVersionChange: (versionId: string) => void;
  launchButtonVersions: Array<{ 
    id: string; 
    label: string; 
    icon?: string; 
    isCustom?: boolean; 
    profileId: string; 
  }>;
  className?: string;
  displayMode?: 'playerName' | 'logo';
}

export function PlayerActionsDisplay({
  playerName,
  launchButtonDefaultVersion,
  onLaunchVersionChange,
  launchButtonVersions,
  className,
  displayMode = 'playerName',
}: PlayerActionsDisplayProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);
  const featureMode = useThemeStore((state) => state.featureMode);
  const setFeatureMode = useThemeStore((state) => state.setFeatureMode);
  const { selectedPoseId } = usePoseStore();
  const { startSession, endSession } = usePlaytimeStore();
  const skinViewerRef = useRef<skinview3d.SkinViewer | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const mouseRotationRef = useRef<number>(0);
  const animFrameRef = useRef<number>(0);
  const [skinUrl, setSkinUrl] = useState<string | null>(null);
const skinRevision = useSkinStore((state) => state.skinRevision);
  const navigate = useNavigate();
  const activeAccount = useMinecraftAuthStore((state) => state.activeAccount);

  const { profiles } = useProfileStore();

  // Determine if we're still loading profiles (no profiles loaded yet)
  const isLoadingProfiles = profiles.length === 0;

  // Get the profile ID to use for featured server launch
  // Option A: Use currently selected profile from MainLaunchButton
  // Option B: Use dedicated profile ID if configured
  const getFeaturedServerProfileId = (): string | null => {
    // Option B: If dedicated profile ID is set, use it
    if (FEATURED_SERVER.profileId) {
      return FEATURED_SERVER.profileId;
    }

    // Option A: Use currently selected profile from MainLaunchButton
    const selectedVersion = launchButtonVersions.find(v => v.id === launchButtonDefaultVersion);
    return selectedVersion?.profileId || null;
  };

  const featuredServerProfileId = getFeaturedServerProfileId();

  // Handle mods button for featured server
  const handleFeaturedServerMods = () => {
    if (!featuredServerProfileId) {
      toast.error(t('profiles.errors.no_profile_selected'));
      return;
    }

    // Navigate to profile detail view (which has mods tab)
    navigate(`/profilesv2/${featuredServerProfileId}`);
  };

  useEffect(() => {
    const loadSkin = async () => {
      try {
        const activeSkin = await MinecraftSkinService.getActiveSkin().catch(() => null);
        if (activeSkin?.base64_data) {
          setSkinUrl(`data:image/png;base64,${activeSkin.base64_data}`);
        } else if (activeAccount?.id) {
          setSkinUrl(`https://api.mineatar.com/skin/${activeAccount.id}`);
        } else {
          setSkinUrl('https://api.mineatar.com/skin/Steve');
        }
      } catch {
        setSkinUrl('https://api.mineatar.com/skin/Steve');
      }
    };
    loadSkin();
  }, [playerName, skinRevision, activeAccount?.id]);

  const applySelectedPose = useCallback((viewer: skinview3d.SkinViewer) => {
    viewer.renderer.setClearColor(0x000000, 0);
    viewer.controls.enabled = false;
    skinViewerRef.current = viewer;
    const pose = POSES.find(p => p.id === selectedPoseId) ?? POSES[0];
    pose.apply(viewer);
  }, [selectedPoseId]);

  // Mouse-reactive skin rotation
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const onMouseMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const relX = (e.clientX - centerX) / (rect.width / 2);
      mouseRotationRef.current = relX * 0.6;
    };

    const animate = () => {
      if (skinViewerRef.current?.playerObject) {
        const target = mouseRotationRef.current;
        const current = skinViewerRef.current.playerObject.rotation.y;
        skinViewerRef.current.playerObject.rotation.y += (target - current) * 0.08;
      }
      animFrameRef.current = requestAnimationFrame(animate);
    };

    container.addEventListener("mousemove", onMouseMove);
    animFrameRef.current = requestAnimationFrame(animate);

    return () => {
      container.removeEventListener("mousemove", onMouseMove);
      cancelAnimationFrame(animFrameRef.current);
    };
  }, []);

  const dropShadowX = '2px';
  const dropShadowY = '4px';
  const dropShadowBlur = '6px';
  const commonDropShadowStyle = `drop-shadow(${dropShadowX} ${dropShadowY} ${dropShadowBlur} ${accentColor.value})`;
  
  const skinViewerDisplayHeight = 500;
  const skinViewerMaxDisplayWidth = 380;

  const selectedVersionLabel = launchButtonVersions.find(v => v.id === launchButtonDefaultVersion)?.label;

  return (
    <div className={cn("flex flex-col items-center", className)}>

      {displayMode === 'logo' ? (
        <img
          src="doofie_logo_color.png"
          alt="Doofie Logo"
          className="h-48 sm:h-56 md:h-64 mb-[-80px] sm:mb-[-100px] md:mb-[-120px] relative z-0"
          style={{
            imageRendering: "pixelated",
            filter: commonDropShadowStyle
          }}
        />
      ) : (
        <h2 className="font-minecraft text-6xl text-center text-white mb-2 lowercase font-normal">
          {playerName || "no account"}
        </h2>
      )}

      <div className={cn(
        "relative w-full max-w-[500px] flex flex-col items-center",
        displayMode === 'logo' && "z-10"
      )}>

        <div ref={containerRef} style={{ filter: 'drop-shadow(5px 10px 5px rgba(0,0,0,0.75))', height: `${skinViewerDisplayHeight}px`, width: `${skinViewerMaxDisplayWidth}px`, flexShrink: 0 }}>
          <SkinView3DWrapper
            skinUrl={skinUrl}
            width={skinViewerMaxDisplayWidth}
            height={skinViewerDisplayHeight}
            zoom={0.9}
            onViewerReady={applySelectedPose}
          />
        </div>

        {/* Don't render launch button while profiles are still loading to prevent flicker */}
        {!isLoadingProfiles && (
          <>
            {/* Featured Server Toggle - above the launch button */}
            <div
              className={`absolute left-0 right-0 flex justify-center px-4 z-30 transition-all duration-300 ${featureMode ? 'bottom-40' : 'bottom-32'}`}
            >
              <button
                onClick={() => setFeatureMode(!featureMode)}
                className="font-minecraft text-2xl lowercase text-white/70 hover:text-white transition-all duration-200 cursor-pointer bg-transparent border-none p-0 whitespace-nowrap text-shadow"
                title={featureMode ? "Switch to Main Launch" : `Switch to ${FEATURED_SERVER.name}`}
              >
                {featureMode ? "switch to main launch" : FEATURED_SERVER.name.toLowerCase()}
              </button>
            </div>
            <div className="absolute bottom-8 left-0 right-0 flex justify-center px-4">
              {featureMode ? (
                // Show featured server card with MOTD
                <ServerLaunchCard
                  serverAddress={FEATURED_SERVER.address}
                  serverName={FEATURED_SERVER.name}
                  profileId={featuredServerProfileId}
                  onMods={handleFeaturedServerMods}
                />
              ) : (
                <div className="max-w-xs sm:max-w-sm">
                  <MainLaunchButton
                    defaultVersion={launchButtonDefaultVersion}
                    onVersionChange={onLaunchVersionChange}
                    versions={launchButtonVersions}
                    selectedVersionLabel={selectedVersionLabel}
                    mainButtonWidth="w-80"
                    maxWidth="400px"
                    mainButtonHeight="h-20"
                  />
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
} 