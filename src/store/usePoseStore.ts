import { create } from "zustand";
import { persist } from "zustand/middleware";
import * as skinview3d from "skinview3d";

export interface PoseDef {
  id: string;
  name: string;
  icon: string;
  description: string;
  animated: boolean;
  apply: (viewer: skinview3d.SkinViewer) => void;
}

export const POSES: PoseDef[] = [
  {
    id: "kneeling",
    name: "Kneeling",
    icon: "solar:star-bold",
    description: "Ein Knie am Boden, Arm erhoben",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = 0.25;
      p.skin.leftArm.rotation.x = -2.1; p.skin.leftArm.rotation.z = 0.35;
      p.skin.rightArm.rotation.x = 0.5; p.skin.rightArm.rotation.z = -0.6;
      p.skin.rightLeg.rotation.x = 1.5;
      p.skin.leftLeg.rotation.x = -0.35;
      p.skin.head.rotation.x = 0.1;
    },
  },
  {
    id: "standing",
    name: "Standing",
    icon: "solar:user-bold",
    description: "Aufrecht stehend, entspannte Haltung",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = 0; p.skin.body.rotation.z = 0;
      p.skin.leftArm.rotation.x = 0; p.skin.leftArm.rotation.z = 0.1;
      p.skin.rightArm.rotation.x = 0; p.skin.rightArm.rotation.z = -0.1;
      p.skin.leftLeg.rotation.x = 0;
      p.skin.rightLeg.rotation.x = 0;
      p.skin.head.rotation.x = 0; p.skin.head.rotation.y = 0;
    },
  },
  {
    id: "walking",
    name: "Walking",
    icon: "solar:walking-bold",
    description: "Gehanimation",
    animated: true,
    apply: (v) => {
      v.animation = new skinview3d.WalkingAnimation();
    },
  },
  {
    id: "running",
    name: "Running",
    icon: "solar:running-bold",
    description: "Schnelle Laufanimation",
    animated: true,
    apply: (v) => {
      const anim = new skinview3d.RunningAnimation();
      v.animation = anim;
    },
  },
  {
    id: "flying",
    name: "Flying",
    icon: "solar:plain-bold",
    description: "Flugpose mit ausgebreiteten Armen",
    animated: true,
    apply: (v) => {
      v.animation = new skinview3d.FlyingAnimation();
    },
  },
  {
    id: "waving",
    name: "Waving",
    icon: "solar:hand-stars-bold",
    description: "Winkt mit der rechten Hand",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = 0; p.skin.body.rotation.z = 0;
      p.skin.leftArm.rotation.x = 0; p.skin.leftArm.rotation.z = 0.15;
      p.skin.rightArm.rotation.x = -2.4; p.skin.rightArm.rotation.z = 0.3;
      p.skin.leftLeg.rotation.x = 0; p.skin.rightLeg.rotation.x = 0;
      p.skin.head.rotation.x = 0; p.skin.head.rotation.y = 0.25;
    },
  },
  {
    id: "victory",
    name: "Victory",
    icon: "solar:medal-ribbons-star-bold",
    description: "Beide Arme triumphierend hochgereckt",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = -0.1;
      p.skin.leftArm.rotation.x = -2.6; p.skin.leftArm.rotation.z = -0.4;
      p.skin.rightArm.rotation.x = -2.6; p.skin.rightArm.rotation.z = 0.4;
      p.skin.leftLeg.rotation.x = 0; p.skin.rightLeg.rotation.x = 0;
      p.skin.head.rotation.x = -0.15;
    },
  },
  {
    id: "crossed",
    name: "Crossed Arms",
    icon: "solar:shield-bold",
    description: "Arme verschränkt — selbstsicher",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = 0;
      p.skin.leftArm.rotation.x = 1.1; p.skin.leftArm.rotation.z = -0.8;
      p.skin.rightArm.rotation.x = 1.1; p.skin.rightArm.rotation.z = 0.8;
      p.skin.leftLeg.rotation.x = 0; p.skin.rightLeg.rotation.x = 0;
      p.skin.head.rotation.x = 0; p.skin.head.rotation.y = 0.15;
    },
  },
  {
    id: "pointing",
    name: "Pointing",
    icon: "solar:cursor-bold",
    description: "Zeigt mit dem rechten Arm nach vorne",
    animated: false,
    apply: (v) => {
      v.animation = null;
      const p = v.playerObject; if (!p) return;
      p.rotation.y = 0;
      p.skin.body.rotation.x = 0;
      p.skin.leftArm.rotation.x = 0; p.skin.leftArm.rotation.z = 0.1;
      p.skin.rightArm.rotation.x = -1.2; p.skin.rightArm.rotation.z = -0.2;
      p.skin.leftLeg.rotation.x = 0; p.skin.rightLeg.rotation.x = 0;
      p.skin.head.rotation.x = -0.1; p.skin.head.rotation.y = -0.2;
    },
  },
  {
    id: "idle",
    name: "Idle",
    icon: "solar:ghost-bold",
    description: "Sanfte Atembewegung",
    animated: true,
    apply: (v) => {
      v.animation = new skinview3d.IdleAnimation();
    },
  },
];

interface PoseState {
  selectedPoseId: string;
  setSelectedPose: (id: string) => void;
}

export const usePoseStore = create<PoseState>()(
  persist(
    (set) => ({
      selectedPoseId: "kneeling",
      setSelectedPose: (id) => set({ selectedPoseId: id }),
    }),
    { name: "doofie-pose-storage" }
  )
);
