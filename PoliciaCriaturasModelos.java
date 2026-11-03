package com.example.policia;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

// GENERADO a partir de la vista previa "Criaturas de Oficios" (convertir.py):
// huesos, cajas y texturas de cada criatura en coordenadas de Minecraft.
// No editar a mano: se regenera desde la vista previa.
public class PoliciaCriaturasModelos {
    static LayerDefinition ciervo() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        PartDefinition p_cuerpo = root.addOrReplaceChild("cuerpo", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -4f, -9f, 8f, 8f, 18f).texOffs(53, 0).addBox(-3.5f, 3.5f, -6f, 7f, 1f, 13f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_cuello = p_cuerpo.addOrReplaceChild("cuello", CubeListBuilder.create().texOffs(94, 0).addBox(-2f, -9f, -2f, 4f, 9f, 4f), PartPose.offsetAndRotation(0f, -2f, -8f, -0.55f, 0f, 0f));
        PartDefinition p_cabeza = p_cuello.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(0, 27).addBox(-2.5f, -4f, -4f, 5f, 5f, 6f).texOffs(0, 39).addBox(-1.5f, -2f, -8f, 3f, 3f, 4f).texOffs(18, 47).addBox(-0.5f, -2f, -8.1f, 1f, 1f, 0.5f).texOffs(114, 39).addBox(-2.6f, -3f, -3.2f, 0.6f, 1f, 1.2f).texOffs(119, 39).addBox(2f, -3f, -3.2f, 0.6f, 1f, 1.2f), PartPose.offsetAndRotation(0f, -9f, 0f, 0.75f, 0f, 0f));
        PartDefinition p_orejaD = p_cabeza.addOrReplaceChild("orejaD", CubeListBuilder.create().texOffs(0, 47).addBox(-3f, -1f, -0.5f, 3f, 1.5f, 1f), PartPose.offsetAndRotation(-2.5f, -3f, 1f, 0f, 0f, -0.5f));
        PartDefinition p_astaD = p_cabeza.addOrReplaceChild("astaD", CubeListBuilder.create().texOffs(111, 27).addBox(-0.6f, -6f, -0.6f, 1.2f, 6f, 1.2f).texOffs(34, 39).addBox(-0.5f, -3.5f, -3f, 1f, 1f, 3f), PartPose.offsetAndRotation(-1.5f, -4f, 0f, 0.1882f, 0.0682f, -0.3436f));
        PartDefinition p_asta2D = p_astaD.addOrReplaceChild("asta2D", CubeListBuilder.create().texOffs(15, 39).addBox(-0.5f, -5f, -0.5f, 1f, 5f, 1f).texOffs(43, 39).addBox(-0.5f, -3f, -2.5f, 1f, 1f, 3f).texOffs(22, 47).addBox(-2.5f, -4.2f, -0.5f, 2f, 1f, 1f), PartPose.offsetAndRotation(0f, -6f, 0f, -0.3017f, -0.1802f, -0.5225f));
        PartDefinition p_orejaI = p_cabeza.addOrReplaceChild("orejaI", CubeListBuilder.create().texOffs(9, 47).addBox(0f, -1f, -0.5f, 3f, 1.5f, 1f), PartPose.offsetAndRotation(2.5f, -3f, 1f, 0f, 0f, 0.5f));
        PartDefinition p_astaI = p_cabeza.addOrReplaceChild("astaI", CubeListBuilder.create().texOffs(117, 27).addBox(-0.6f, -6f, -0.6f, 1.2f, 6f, 1.2f).texOffs(52, 39).addBox(-0.5f, -3.5f, -3f, 1f, 1f, 3f), PartPose.offsetAndRotation(1.5f, -4f, 0f, 0.1882f, -0.0682f, 0.3436f));
        PartDefinition p_asta2I = p_astaI.addOrReplaceChild("asta2I", CubeListBuilder.create().texOffs(20, 39).addBox(-0.5f, -5f, -0.5f, 1f, 5f, 1f).texOffs(61, 39).addBox(-0.5f, -3f, -2.5f, 1f, 1f, 3f).texOffs(29, 47).addBox(0.5f, -4.2f, -0.5f, 2f, 1f, 1f), PartPose.offsetAndRotation(0f, -6f, 0f, -0.3017f, 0.1802f, 0.5225f));
        PartDefinition p_musloDD = p_cuerpo.addOrReplaceChild("musloDD", CubeListBuilder.create().texOffs(23, 27).addBox(-1.5f, 0f, -1.5f, 3f, 7f, 3f), PartPose.offsetAndRotation(-3f, 2f, -7f, 0f, 0f, 0f));
        PartDefinition p_canaDD = p_musloDD.addOrReplaceChild("canaDD", CubeListBuilder.create().texOffs(75, 27).addBox(-1f, 0f, -1f, 2f, 7f, 2f).texOffs(70, 39).addBox(-1.2f, 7f, -1.3f, 2.4f, 1f, 2.6f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloDI = p_cuerpo.addOrReplaceChild("musloDI", CubeListBuilder.create().texOffs(36, 27).addBox(-1.5f, 0f, -1.5f, 3f, 7f, 3f), PartPose.offsetAndRotation(3f, 2f, -7f, 0f, 0f, 0f));
        PartDefinition p_canaDI = p_musloDI.addOrReplaceChild("canaDI", CubeListBuilder.create().texOffs(84, 27).addBox(-1f, 0f, -1f, 2f, 7f, 2f).texOffs(81, 39).addBox(-1.2f, 7f, -1.3f, 2.4f, 1f, 2.6f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloTD = p_cuerpo.addOrReplaceChild("musloTD", CubeListBuilder.create().texOffs(49, 27).addBox(-1.5f, 0f, -1.5f, 3f, 7f, 3f), PartPose.offsetAndRotation(-3f, 2f, 7f, 0f, 0f, 0f));
        PartDefinition p_canaTD = p_musloTD.addOrReplaceChild("canaTD", CubeListBuilder.create().texOffs(93, 27).addBox(-1f, 0f, -1f, 2f, 7f, 2f).texOffs(92, 39).addBox(-1.2f, 7f, -1.3f, 2.4f, 1f, 2.6f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloTI = p_cuerpo.addOrReplaceChild("musloTI", CubeListBuilder.create().texOffs(62, 27).addBox(-1.5f, 0f, -1.5f, 3f, 7f, 3f), PartPose.offsetAndRotation(3f, 2f, 7f, 0f, 0f, 0f));
        PartDefinition p_canaTI = p_musloTI.addOrReplaceChild("canaTI", CubeListBuilder.create().texOffs(102, 27).addBox(-1f, 0f, -1f, 2f, 7f, 2f).texOffs(103, 39).addBox(-1.2f, 7f, -1.3f, 2.4f, 1f, 2.6f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_cola = p_cuerpo.addOrReplaceChild("cola", CubeListBuilder.create().texOffs(25, 39).addBox(-1f, -1f, -0.5f, 2f, 4f, 2f), PartPose.offsetAndRotation(0f, -2f, 9f, 0.6f, 0f, 0f));
        return LayerDefinition.create(md, 128, 64);
    }

    static LayerDefinition escorpion() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        PartDefinition p_cuerpo = root.addOrReplaceChild("cuerpo", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -3f, -6f, 10f, 5f, 12f).texOffs(45, 0).addBox(-4f, -4f, -5f, 8f, 1f, 10f).texOffs(19, 18).addBox(-4f, -2.5f, -10f, 8f, 4f, 4f).texOffs(63, 48).addBox(-3.5f, -2.5f, -10.2f, 1f, 1f, 0.6f).texOffs(68, 48).addBox(-1.5f, -2.5f, -10.2f, 1f, 1f, 0.6f).texOffs(73, 48).addBox(0.5f, -2.5f, -10.2f, 1f, 1f, 0.6f).texOffs(78, 48).addBox(2.5f, -2.5f, -10.2f, 1f, 1f, 0.6f), PartPose.offsetAndRotation(0f, 17f, 0f, 0f, 0f, 0f));
        PartDefinition p_pataD0 = p_cuerpo.addOrReplaceChild("pataD0", CubeListBuilder.create().texOffs(20, 37).addBox(-6f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(-5f, 0f, -4f, -0.2781f, -0.4211f, 0.6098f));
        PartDefinition p_patabD0 = p_pataD0.addOrReplaceChild("patabD0", CubeListBuilder.create().texOffs(37, 37).addBox(-7f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(-6f, 0f, 0f, 0f, 0f, -1.65f));
        PartDefinition p_pataD1 = p_cuerpo.addOrReplaceChild("pataD1", CubeListBuilder.create().texOffs(56, 37).addBox(-6f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(-5f, 0f, -1f, -0.0895f, -0.1447f, 0.5565f));
        PartDefinition p_patabD1 = p_pataD1.addOrReplaceChild("patabD1", CubeListBuilder.create().texOffs(73, 37).addBox(-7f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(-6f, 0f, 0f, 0f, 0f, -1.65f));
        PartDefinition p_pataD2 = p_cuerpo.addOrReplaceChild("pataD2", CubeListBuilder.create().texOffs(92, 37).addBox(-6f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(-5f, 0f, 2f, 0.0842f, 0.1362f, 0.5557f));
        PartDefinition p_patabD2 = p_pataD2.addOrReplaceChild("patabD2", CubeListBuilder.create().texOffs(109, 37).addBox(-7f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(-6f, 0f, 0f, 0f, 0f, -1.65f));
        PartDefinition p_pataD3 = p_cuerpo.addOrReplaceChild("pataD3", CubeListBuilder.create().texOffs(0, 43).addBox(-6f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(-5f, 0f, 5f, 0.2719f, 0.4128f, 0.6073f));
        PartDefinition p_patabD3 = p_pataD3.addOrReplaceChild("patabD3", CubeListBuilder.create().texOffs(17, 43).addBox(-7f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(-6f, 0f, 0f, 0f, 0f, -1.65f));
        PartDefinition p_brazoD = p_cuerpo.addOrReplaceChild("brazoD", CubeListBuilder.create().texOffs(82, 0).addBox(-1.25f, -1.25f, -7f, 2.5f, 2.5f, 7f), PartPose.offsetAndRotation(-4f, -0.5f, -8f, 0.121f, 0.5966f, 0.0682f));
        PartDefinition p_anteD = p_brazoD.addOrReplaceChild("anteD", CubeListBuilder.create().texOffs(44, 18).addBox(-1.5f, -1.5f, -5f, 3f, 3f, 5f), PartPose.offsetAndRotation(0f, 0f, -7f, 0f, -1.1f, 0f));
        PartDefinition p_pinzaD = p_anteD.addOrReplaceChild("pinzaD", CubeListBuilder.create().texOffs(61, 18).addBox(-2f, -2f, -5f, 4f, 3f, 5f).texOffs(0, 37).addBox(-2f, -2f, -8f, 1.5f, 2f, 3f), PartPose.offsetAndRotation(0f, 0f, -5f, 0f, 0f, 0f));
        PartDefinition p_dedoD = p_pinzaD.addOrReplaceChild("dedoD", CubeListBuilder.create().texOffs(80, 18).addBox(-0.75f, 0f, -6f, 1.5f, 1.5f, 6f), PartPose.offsetAndRotation(1f, 1f, -2f, 0f, 0f, 0f));
        PartDefinition p_pataI0 = p_cuerpo.addOrReplaceChild("pataI0", CubeListBuilder.create().texOffs(36, 43).addBox(0f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(5f, 0f, -4f, -0.2781f, 0.4211f, -0.6098f));
        PartDefinition p_patabI0 = p_pataI0.addOrReplaceChild("patabI0", CubeListBuilder.create().texOffs(53, 43).addBox(0f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f, 0f, 1.65f));
        PartDefinition p_pataI1 = p_cuerpo.addOrReplaceChild("pataI1", CubeListBuilder.create().texOffs(72, 43).addBox(0f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(5f, 0f, -1f, -0.0895f, 0.1447f, -0.5565f));
        PartDefinition p_patabI1 = p_pataI1.addOrReplaceChild("patabI1", CubeListBuilder.create().texOffs(89, 43).addBox(0f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f, 0f, 1.65f));
        PartDefinition p_pataI2 = p_cuerpo.addOrReplaceChild("pataI2", CubeListBuilder.create().texOffs(108, 43).addBox(0f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(5f, 0f, 2f, 0.0842f, -0.1362f, -0.5557f));
        PartDefinition p_patabI2 = p_pataI2.addOrReplaceChild("patabI2", CubeListBuilder.create().texOffs(0, 48).addBox(0f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f, 0f, 1.65f));
        PartDefinition p_pataI3 = p_cuerpo.addOrReplaceChild("pataI3", CubeListBuilder.create().texOffs(19, 48).addBox(0f, -1f, -1f, 6f, 2f, 2f), PartPose.offsetAndRotation(5f, 0f, 5f, 0.2719f, -0.4128f, -0.6073f));
        PartDefinition p_patabI3 = p_pataI3.addOrReplaceChild("patabI3", CubeListBuilder.create().texOffs(36, 48).addBox(0f, -0.8f, -0.8f, 7f, 1.6f, 1.6f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f, 0f, 1.65f));
        PartDefinition p_brazoI = p_cuerpo.addOrReplaceChild("brazoI", CubeListBuilder.create().texOffs(102, 0).addBox(-1.25f, -1.25f, -7f, 2.5f, 2.5f, 7f), PartPose.offsetAndRotation(4f, -0.5f, -8f, 0.121f, -0.5966f, -0.0682f));
        PartDefinition p_anteI = p_brazoI.addOrReplaceChild("anteI", CubeListBuilder.create().texOffs(96, 18).addBox(-1.5f, -1.5f, -5f, 3f, 3f, 5f), PartPose.offsetAndRotation(0f, 0f, -7f, 0f, 1.1f, 0f));
        PartDefinition p_pinzaI = p_anteI.addOrReplaceChild("pinzaI", CubeListBuilder.create().texOffs(0, 28).addBox(-2f, -2f, -5f, 4f, 3f, 5f).texOffs(10, 37).addBox(0.5f, -2f, -8f, 1.5f, 2f, 3f), PartPose.offsetAndRotation(0f, 0f, -5f, 0f, 0f, 0f));
        PartDefinition p_dedoI = p_pinzaI.addOrReplaceChild("dedoI", CubeListBuilder.create().texOffs(19, 28).addBox(-0.75f, 0f, -6f, 1.5f, 1.5f, 6f), PartPose.offsetAndRotation(-1f, 1f, -2f, 0f, 0f, 0f));
        PartDefinition p_cola0 = p_cuerpo.addOrReplaceChild("cola0", CubeListBuilder.create().texOffs(0, 18).addBox(-2.5f, -2.5f, 0f, 5f, 4.5f, 4f), PartPose.offsetAndRotation(0f, -2f, 6f, 0.55f, 0f, 0f));
        PartDefinition p_cola1 = p_cola0.addOrReplaceChild("cola1", CubeListBuilder.create().texOffs(35, 28).addBox(-2.225f, -2.225f, 0f, 4.45f, 3.95f, 4f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.5f, 0f, 0f));
        PartDefinition p_cola2 = p_cola1.addOrReplaceChild("cola2", CubeListBuilder.create().texOffs(53, 28).addBox(-1.95f, -1.95f, 0f, 3.9f, 3.4f, 4f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.5f, 0f, 0f));
        PartDefinition p_cola3 = p_cola2.addOrReplaceChild("cola3", CubeListBuilder.create().texOffs(70, 28).addBox(-1.675f, -1.675f, 0f, 3.35f, 2.85f, 4f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.5f, 0f, 0f));
        PartDefinition p_cola4 = p_cola3.addOrReplaceChild("cola4", CubeListBuilder.create().texOffs(86, 28).addBox(-1.4f, -1.4f, 0f, 2.8f, 2.3f, 4f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.5f, 0f, 0f));
        PartDefinition p_cola5 = p_cola4.addOrReplaceChild("cola5", CubeListBuilder.create().texOffs(101, 28).addBox(-1.125f, -1.125f, 0f, 2.25f, 1.75f, 4f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.5f, 0f, 0f));
        PartDefinition p_aguijon = p_cola5.addOrReplaceChild("aguijon", CubeListBuilder.create().texOffs(115, 28).addBox(-1.5f, -1.5f, 0f, 3f, 3f, 3f).texOffs(55, 48).addBox(-0.5f, 1.5f, 2f, 1f, 1f, 2.5f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.9f, 0f, 0f));
        return LayerDefinition.create(md, 128, 64);
    }

    static LayerDefinition yeti() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        PartDefinition p_cadera = root.addOrReplaceChild("cadera", CubeListBuilder.create().texOffs(40, 23).addBox(-5f, -3f, -3.5f, 10f, 6f, 7f), PartPose.offsetAndRotation(0f, 11f, 0f, 0f, 0f, 0f));
        PartDefinition p_torso = p_cadera.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -13f, -4.5f, 14f, 13f, 9f).texOffs(0, 39).addBox(-4.5f, -10f, -5.1f, 9f, 9f, 0.6f), PartPose.offsetAndRotation(0f, -3f, 0f, 0.28f, 0f, 0f));
        PartDefinition p_cabeza = p_torso.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(47, 0).addBox(-4f, -7f, -4.5f, 8f, 8f, 8f).texOffs(73, 50).addBox(-3f, -5f, -5.3f, 6f, 5f, 0.8f).texOffs(73, 60).addBox(-2.6f, -4f, -5.6f, 1.6f, 1f, 0.3f).texOffs(78, 60).addBox(1f, -4f, -5.6f, 1.6f, 1f, 0.3f).texOffs(56, 60).addBox(-3.2f, -5.2f, -5.4f, 6.4f, 1.2f, 1.2f), PartPose.offsetAndRotation(0f, -12.5f, -2.5f, -0.28f, 0f, 0f));
        PartDefinition p_mandibula = p_cabeza.addOrReplaceChild("mandibula", CubeListBuilder.create().texOffs(53, 50).addBox(-2.5f, 0f, -4.3f, 5f, 2f, 4.3f).texOffs(83, 60).addBox(-2.2f, -0.8f, -4.4f, 0.8f, 1.2f, 0.4f).texOffs(87, 60).addBox(1.4f, -0.8f, -4.4f, 0.8f, 1.2f, 0.4f), PartPose.offsetAndRotation(0f, 0f, -1f, 0f, 0f, 0f));
        PartDefinition p_brazoD = p_torso.addOrReplaceChild("brazoD", CubeListBuilder.create().texOffs(80, 0).addBox(-2.25f, -1f, -2.5f, 4.5f, 11f, 5f), PartPose.offsetAndRotation(-8.5f, -11f, 0f, 0f, 0f, 0.12f));
        PartDefinition p_anteD = p_brazoD.addOrReplaceChild("anteD", CubeListBuilder.create().texOffs(0, 23).addBox(-2.25f, 0f, -2.5f, 4.5f, 10f, 5f), PartPose.offsetAndRotation(0f, 10f, 0f, -0.3f, 0f, 0f));
        PartDefinition p_manoD = p_anteD.addOrReplaceChild("manoD", CubeListBuilder.create().texOffs(63, 39).addBox(-2.6f, 0f, -3f, 5.2f, 3f, 6f), PartPose.offsetAndRotation(0f, 10f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloD = p_cadera.addOrReplaceChild("musloD", CubeListBuilder.create().texOffs(75, 23).addBox(-2.5f, 0f, -2.5f, 5f, 6f, 5f), PartPose.offsetAndRotation(-3f, 2f, 0f, 0f, 0f, 0f));
        PartDefinition p_piernaD = p_musloD.addOrReplaceChild("piernaD", CubeListBuilder.create().texOffs(21, 39).addBox(-2.5f, 0f, -2.5f, 5f, 5f, 5f).texOffs(87, 39).addBox(-3f, 5f, -4.5f, 6f, 1f, 8f), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0f));
        PartDefinition p_mechHombroD = p_torso.addOrReplaceChild("mechHombroD", CubeListBuilder.create().texOffs(88, 50).addBox(-1.5f, 0f, -1.5f, 3f, 3f, 3f), PartPose.offsetAndRotation(-7f, -12.5f, 1f, 0f, 0f, -0.3f));
        PartDefinition p_mechCodoD = p_anteD.addOrReplaceChild("mechCodoD", CubeListBuilder.create().texOffs(101, 50).addBox(-1.5f, 0f, -1f, 3f, 4f, 2f), PartPose.offsetAndRotation(0f, 1f, 2.5f, 0.4f, 0f, 0f));
        PartDefinition p_brazoI = p_torso.addOrReplaceChild("brazoI", CubeListBuilder.create().texOffs(100, 0).addBox(-2.25f, -1f, -2.5f, 4.5f, 11f, 5f), PartPose.offsetAndRotation(8.5f, -11f, 0f, 0f, 0f, -0.12f));
        PartDefinition p_anteI = p_brazoI.addOrReplaceChild("anteI", CubeListBuilder.create().texOffs(20, 23).addBox(-2.25f, 0f, -2.5f, 4.5f, 10f, 5f), PartPose.offsetAndRotation(0f, 10f, 0f, -0.3f, 0f, 0f));
        PartDefinition p_manoI = p_anteI.addOrReplaceChild("manoI", CubeListBuilder.create().texOffs(0, 50).addBox(-2.6f, 0f, -3f, 5.2f, 3f, 6f), PartPose.offsetAndRotation(0f, 10f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloI = p_cadera.addOrReplaceChild("musloI", CubeListBuilder.create().texOffs(96, 23).addBox(-2.5f, 0f, -2.5f, 5f, 6f, 5f), PartPose.offsetAndRotation(3f, 2f, 0f, 0f, 0f, 0f));
        PartDefinition p_piernaI = p_musloI.addOrReplaceChild("piernaI", CubeListBuilder.create().texOffs(42, 39).addBox(-2.5f, 0f, -2.5f, 5f, 5f, 5f).texOffs(24, 50).addBox(-3f, 5f, -4.5f, 6f, 1f, 8f), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0f));
        PartDefinition p_mechHombroI = p_torso.addOrReplaceChild("mechHombroI", CubeListBuilder.create().texOffs(112, 50).addBox(-1.5f, 0f, -1.5f, 3f, 3f, 3f), PartPose.offsetAndRotation(7f, -12.5f, 1f, 0f, 0f, 0.3f));
        PartDefinition p_mechCodoI = p_anteI.addOrReplaceChild("mechCodoI", CubeListBuilder.create().texOffs(0, 60).addBox(-1.5f, 0f, -1f, 3f, 4f, 2f), PartPose.offsetAndRotation(0f, 1f, 2.5f, 0.4f, 0f, 0f));
        PartDefinition p_mechEsp0 = p_torso.addOrReplaceChild("mechEsp0", CubeListBuilder.create().texOffs(11, 60).addBox(-3f, 0f, -0.5f, 6f, 4f, 2f), PartPose.offsetAndRotation(0f, -12f, 4.5f, -0.3f, 0f, 0f));
        PartDefinition p_mechEsp1 = p_torso.addOrReplaceChild("mechEsp1", CubeListBuilder.create().texOffs(28, 60).addBox(-2.5f, 0f, -0.5f, 5f, 4f, 2f), PartPose.offsetAndRotation(0f, -8f, 4.5f, -0.3f, 0f, 0f));
        PartDefinition p_mechEsp2 = p_torso.addOrReplaceChild("mechEsp2", CubeListBuilder.create().texOffs(43, 60).addBox(-2f, 0f, -0.5f, 4f, 4f, 2f), PartPose.offsetAndRotation(0f, -4f, 4.5f, -0.3f, 0f, 0f));
        return LayerDefinition.create(md, 128, 80);
    }

    static LayerDefinition sapo() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        PartDefinition p_cuerpo = root.addOrReplaceChild("cuerpo", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -5f, -6f, 14f, 8f, 13f), PartPose.offsetAndRotation(0f, 17f, 0f, 0f, 0f, 0f));
        PartDefinition p_panza = p_cuerpo.addOrReplaceChild("panza", CubeListBuilder.create().texOffs(55, 0).addBox(-6f, -0.5f, -5f, 12f, 2f, 11f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_cabeza = p_cuerpo.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(0, 22).addBox(-6.5f, -4f, -6.5f, 13f, 5f, 7f), PartPose.offsetAndRotation(0f, 0f, -5.5f, 0f, 0f, 0f));
        PartDefinition p_ojoD = p_cabeza.addOrReplaceChild("ojoD", CubeListBuilder.create().texOffs(25, 46).addBox(-1.75f, -2.5f, -1.75f, 3.5f, 3f, 3.5f).texOffs(39, 55).addBox(-0.8f, -2f, -2f, 1.6f, 1.4f, 0.3f), PartPose.offsetAndRotation(-4f, -4f, -2.5f, 0f, 0f, 0f));
        PartDefinition p_ojoI = p_cabeza.addOrReplaceChild("ojoI", CubeListBuilder.create().texOffs(40, 46).addBox(-1.75f, -2.5f, -1.75f, 3.5f, 3f, 3.5f).texOffs(44, 55).addBox(-0.8f, -2f, -2f, 1.6f, 1.4f, 0.3f), PartPose.offsetAndRotation(4f, -4f, -2.5f, 0f, 0f, 0f));
        PartDefinition p_mandibula = p_cabeza.addOrReplaceChild("mandibula", CubeListBuilder.create().texOffs(21, 35).addBox(-6.5f, 0f, -7f, 13f, 2f, 7f), PartPose.offsetAndRotation(0f, 1f, 0.5f, 0f, 0f, 0f));
        PartDefinition p_buche = p_mandibula.addOrReplaceChild("buche", CubeListBuilder.create().texOffs(62, 35).addBox(-3.5f, -0.5f, -2.5f, 7f, 2.5f, 5f), PartPose.offsetAndRotation(0f, 2f, -4f, 0f, 0f, 0f));
        PartDefinition p_lengua = p_cabeza.addOrReplaceChild("lengua", CubeListBuilder.create().texOffs(32, 55).addBox(-1f, -0.6f, -1f, 2f, 1.2f, 1f), PartPose.offsetAndRotation(0f, 0.5f, -5f, 0f, 0f, 0f));
        PartDefinition p_punta = p_lengua.addOrReplaceChild("punta", CubeListBuilder.create().texOffs(77, 46).addBox(-1.6f, -1.2f, -1.9f, 3.2f, 2.4f, 2.4f), PartPose.offsetAndRotation(0f, 0f, -1f, 0f, 0f, 0f));
        PartDefinition p_musloTD = p_cuerpo.addOrReplaceChild("musloTD", CubeListBuilder.create().texOffs(41, 22).addBox(-3f, -2f, -1f, 4f, 4f, 8f), PartPose.offsetAndRotation(-6.5f, 1f, 4f, 0f, 0.3f, 0f));
        PartDefinition p_canaTD = p_musloTD.addOrReplaceChild("canaTD", CubeListBuilder.create().texOffs(91, 22).addBox(-1.5f, -1.5f, -7f, 3f, 3f, 7f), PartPose.offsetAndRotation(0f, 1f, 6.5f, 0f, 0f, 0f));
        PartDefinition p_pieTD = p_canaTD.addOrReplaceChild("pieTD", CubeListBuilder.create().texOffs(87, 35).addBox(-2.5f, 0f, -6f, 5f, 1f, 7f), PartPose.offsetAndRotation(0f, 1f, -6.5f, 0f, 0f, 0f));
        PartDefinition p_brazoD = p_cuerpo.addOrReplaceChild("brazoD", CubeListBuilder.create().texOffs(55, 46).addBox(-1.25f, 0f, -1.25f, 2.5f, 3f, 2.5f), PartPose.offsetAndRotation(-5.5f, 2f, -5f, 0f, 0f, 0f));
        PartDefinition p_manoDD = p_brazoD.addOrReplaceChild("manoDD", CubeListBuilder.create().texOffs(90, 46).addBox(-1.5f, 0f, -1.2f, 3f, 2f, 2.4f).texOffs(0, 55).addBox(-2.2f, 1.9f, -3f, 4.4f, 0.6f, 3f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloTI = p_cuerpo.addOrReplaceChild("musloTI", CubeListBuilder.create().texOffs(66, 22).addBox(-1f, -2f, -1f, 4f, 4f, 8f), PartPose.offsetAndRotation(6.5f, 1f, 4f, 0f, -0.3f, 0f));
        PartDefinition p_canaTI = p_musloTI.addOrReplaceChild("canaTI", CubeListBuilder.create().texOffs(0, 35).addBox(-1.5f, -1.5f, -7f, 3f, 3f, 7f), PartPose.offsetAndRotation(0f, 1f, 6.5f, 0f, 0f, 0f));
        PartDefinition p_pieTI = p_canaTI.addOrReplaceChild("pieTI", CubeListBuilder.create().texOffs(0, 46).addBox(-2.5f, 0f, -6f, 5f, 1f, 7f), PartPose.offsetAndRotation(0f, 1f, -6.5f, 0f, 0f, 0f));
        PartDefinition p_brazoI = p_cuerpo.addOrReplaceChild("brazoI", CubeListBuilder.create().texOffs(66, 46).addBox(-1.25f, 0f, -1.25f, 2.5f, 3f, 2.5f), PartPose.offsetAndRotation(5.5f, 2f, -5f, 0f, 0f, 0f));
        PartDefinition p_manoDI = p_brazoI.addOrReplaceChild("manoDI", CubeListBuilder.create().texOffs(102, 46).addBox(-1.5f, 0f, -1.2f, 3f, 2f, 2.4f).texOffs(16, 55).addBox(-2.2f, 1.9f, -3f, 4.4f, 0.6f, 3f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        return LayerDefinition.create(md, 128, 64);
    }

    static LayerDefinition golem() {
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        PartDefinition p_cadera = root.addOrReplaceChild("cadera", CubeListBuilder.create().texOffs(74, 45).addBox(-6f, -3f, -4f, 12f, 6f, 8f), PartPose.offsetAndRotation(0f, 8f, 0f, 0f, 0f, 0f));
        PartDefinition p_torso = p_cadera.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-9f, -15f, -5f, 18f, 15f, 10f).texOffs(54, 26).addBox(-9.5f, -16f, -5.5f, 6f, 4f, 11f).texOffs(0, 61).addBox(3.5f, -16f, -5.5f, 6f, 3f, 11f).texOffs(62, 89).addBox(-2.5f, -11f, -6f, 5f, 5f, 1f).texOffs(0, 106).addBox(-4f, -5f, -5.6f, 8f, 1f, 1f), PartPose.offsetAndRotation(0f, -3f, 0f, 0.12f, 0f, 0f));
        PartDefinition p_cabeza = p_torso.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(89, 26).addBox(-4f, -7f, -4f, 8f, 7f, 8f).texOffs(19, 106).addBox(-3f, -4.2f, -4.4f, 2f, 1.2f, 0.4f).texOffs(25, 106).addBox(1f, -4.2f, -4.4f, 2f, 1.2f, 0.4f).texOffs(25, 76).addBox(-4.5f, -8f, -4.5f, 9f, 2f, 9f).texOffs(75, 89).addBox(-3f, -13f, 0f, 1f, 5f, 1f).texOffs(31, 106).addBox(-4f, -12f, 0f, 3f, 1f, 1f).texOffs(0, 100).addBox(2f, -12f, -1f, 1f, 4f, 1f).texOffs(40, 106).addBox(2f, -11f, -1f, 3f, 1f, 1f), PartPose.offsetAndRotation(0f, -15f, 0f, 0f, 0f, 0f));
        PartDefinition p_brazoD = p_torso.addOrReplaceChild("brazoD", CubeListBuilder.create().texOffs(57, 0).addBox(-3f, -1f, -3f, 6f, 12f, 6f).texOffs(62, 76).addBox(-3.5f, -2f, -3.5f, 7f, 3f, 7f), PartPose.offsetAndRotation(-10.5f, -13f, 0f, 0f, 0f, 0.15f));
        PartDefinition p_anteD = p_brazoD.addOrReplaceChild("anteD", CubeListBuilder.create().texOffs(82, 0).addBox(-3.5f, 0f, -3.5f, 7f, 11f, 7f), PartPose.offsetAndRotation(0f, 11f, 0f, -0.25f, 0f, 0f));
        PartDefinition p_punoD = p_anteD.addOrReplaceChild("punoD", CubeListBuilder.create().texOffs(0, 45).addBox(-4.5f, 0f, -4.5f, 9f, 6f, 9f), PartPose.offsetAndRotation(0f, 11f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrD0 = p_anteD.addOrReplaceChild("raizBrD0", CubeListBuilder.create().texOffs(5, 100).addBox(-0.8f, 0f, -0.8f, 1.6f, 3f, 1.6f), PartPose.offsetAndRotation(-3f, 4f, 2f, 0f, 0f, 0f));
        PartDefinition p_raizBrD1 = p_raizBrD0.addOrReplaceChild("raizBrD1", CubeListBuilder.create().texOffs(13, 100).addBox(-0.656f, 0f, -0.656f, 1.312f, 3f, 1.312f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrD2 = p_raizBrD1.addOrReplaceChild("raizBrD2", CubeListBuilder.create().texOffs(20, 100).addBox(-0.512f, 0f, -0.512f, 1.024f, 3f, 1.024f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrD3 = p_raizBrD2.addOrReplaceChild("raizBrD3", CubeListBuilder.create().texOffs(101, 100).addBox(-0.368f, 0f, -0.368f, 0.736f, 3f, 0.736f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloD = p_cadera.addOrReplaceChild("musloD", CubeListBuilder.create().texOffs(35, 61).addBox(-3f, 0f, -3f, 6f, 7f, 6f), PartPose.offsetAndRotation(-4f, 2f, 0f, 0f, 0f, 0f));
        PartDefinition p_piernaD = p_musloD.addOrReplaceChild("piernaD", CubeListBuilder.create().texOffs(85, 61).addBox(-3f, 0f, -3f, 6f, 6f, 6f).texOffs(91, 76).addBox(-3.5f, 6f, -4.5f, 7f, 1f, 9f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_brazoI = p_torso.addOrReplaceChild("brazoI", CubeListBuilder.create().texOffs(0, 26).addBox(-3f, -1f, -3f, 6f, 12f, 6f).texOffs(0, 89).addBox(-3.5f, -2f, -3.5f, 7f, 3f, 7f), PartPose.offsetAndRotation(10.5f, -13f, 0f, 0f, 0f, -0.15f));
        PartDefinition p_anteI = p_brazoI.addOrReplaceChild("anteI", CubeListBuilder.create().texOffs(25, 26).addBox(-3.5f, 0f, -3.5f, 7f, 11f, 7f), PartPose.offsetAndRotation(0f, 11f, 0f, -0.25f, 0f, 0f));
        PartDefinition p_punoI = p_anteI.addOrReplaceChild("punoI", CubeListBuilder.create().texOffs(37, 45).addBox(-4.5f, 0f, -4.5f, 9f, 6f, 9f), PartPose.offsetAndRotation(0f, 11f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrI0 = p_anteI.addOrReplaceChild("raizBrI0", CubeListBuilder.create().texOffs(26, 100).addBox(-0.8f, 0f, -0.8f, 1.6f, 3f, 1.6f), PartPose.offsetAndRotation(3f, 4f, 2f, 0f, 0f, 0f));
        PartDefinition p_raizBrI1 = p_raizBrI0.addOrReplaceChild("raizBrI1", CubeListBuilder.create().texOffs(34, 100).addBox(-0.656f, 0f, -0.656f, 1.312f, 3f, 1.312f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrI2 = p_raizBrI1.addOrReplaceChild("raizBrI2", CubeListBuilder.create().texOffs(41, 100).addBox(-0.512f, 0f, -0.512f, 1.024f, 3f, 1.024f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizBrI3 = p_raizBrI2.addOrReplaceChild("raizBrI3", CubeListBuilder.create().texOffs(105, 100).addBox(-0.368f, 0f, -0.368f, 0.736f, 3f, 0.736f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_musloI = p_cadera.addOrReplaceChild("musloI", CubeListBuilder.create().texOffs(60, 61).addBox(-3f, 0f, -3f, 6f, 7f, 6f), PartPose.offsetAndRotation(4f, 2f, 0f, 0f, 0f, 0f));
        PartDefinition p_piernaI = p_musloI.addOrReplaceChild("piernaI", CubeListBuilder.create().texOffs(0, 76).addBox(-3f, 0f, -3f, 6f, 6f, 6f).texOffs(29, 89).addBox(-3.5f, 6f, -4.5f, 7f, 1f, 9f), PartPose.offsetAndRotation(0f, 7f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC0 = p_cadera.addOrReplaceChild("raizC0", CubeListBuilder.create().texOffs(47, 100).addBox(-0.9f, 0f, -0.9f, 1.8f, 3f, 1.8f), PartPose.offsetAndRotation(-4f, 2f, -3.5f, 0f, 0f, 0f));
        PartDefinition p_raizC1 = p_raizC0.addOrReplaceChild("raizC1", CubeListBuilder.create().texOffs(56, 100).addBox(-0.738f, 0f, -0.738f, 1.476f, 3f, 1.476f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC2 = p_raizC1.addOrReplaceChild("raizC2", CubeListBuilder.create().texOffs(63, 100).addBox(-0.576f, 0f, -0.576f, 1.152f, 3f, 1.152f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC3 = p_raizC2.addOrReplaceChild("raizC3", CubeListBuilder.create().texOffs(109, 100).addBox(-0.414f, 0f, -0.414f, 0.828f, 3f, 0.828f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC20 = p_cadera.addOrReplaceChild("raizC20", CubeListBuilder.create().texOffs(69, 100).addBox(-0.9f, 0f, -0.9f, 1.8f, 3f, 1.8f), PartPose.offsetAndRotation(4f, 2f, -3.5f, 0f, 0f, 0f));
        PartDefinition p_raizC21 = p_raizC20.addOrReplaceChild("raizC21", CubeListBuilder.create().texOffs(78, 100).addBox(-0.738f, 0f, -0.738f, 1.476f, 3f, 1.476f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC22 = p_raizC21.addOrReplaceChild("raizC22", CubeListBuilder.create().texOffs(85, 100).addBox(-0.576f, 0f, -0.576f, 1.152f, 3f, 1.152f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizC23 = p_raizC22.addOrReplaceChild("raizC23", CubeListBuilder.create().texOffs(114, 100).addBox(-0.414f, 0f, -0.414f, 0.828f, 3f, 0.828f), PartPose.offsetAndRotation(0f, 3f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE0 = p_torso.addOrReplaceChild("raizE0", CubeListBuilder.create().texOffs(80, 89).addBox(-1f, 0f, -1f, 2f, 4f, 2f), PartPose.offsetAndRotation(-5f, -2f, 5.5f, 0f, 0f, 0f));
        PartDefinition p_raizE1 = p_raizE0.addOrReplaceChild("raizE1", CubeListBuilder.create().texOffs(89, 89).addBox(-0.82f, 0f, -0.82f, 1.64f, 4f, 1.64f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE2 = p_raizE1.addOrReplaceChild("raizE2", CubeListBuilder.create().texOffs(97, 89).addBox(-0.64f, 0f, -0.64f, 1.28f, 4f, 1.28f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE3 = p_raizE2.addOrReplaceChild("raizE3", CubeListBuilder.create().texOffs(91, 100).addBox(-0.46f, 0f, -0.46f, 0.92f, 4f, 0.92f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE20 = p_torso.addOrReplaceChild("raizE20", CubeListBuilder.create().texOffs(104, 89).addBox(-1f, 0f, -1f, 2f, 4f, 2f), PartPose.offsetAndRotation(5f, -3f, 5.5f, 0f, 0f, 0f));
        PartDefinition p_raizE21 = p_raizE20.addOrReplaceChild("raizE21", CubeListBuilder.create().texOffs(113, 89).addBox(-0.82f, 0f, -0.82f, 1.64f, 4f, 1.64f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE22 = p_raizE21.addOrReplaceChild("raizE22", CubeListBuilder.create().texOffs(121, 89).addBox(-0.64f, 0f, -0.64f, 1.28f, 4f, 1.28f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        PartDefinition p_raizE23 = p_raizE22.addOrReplaceChild("raizE23", CubeListBuilder.create().texOffs(96, 100).addBox(-0.46f, 0f, -0.46f, 0.92f, 4f, 0.92f), PartPose.offsetAndRotation(0f, 4f, 0f, 0f, 0f, 0f));
        return LayerDefinition.create(md, 128, 112);
    }

    static final String[] HUESOS_CIERVO = {"cuerpo", "cuello", "cabeza", "orejaD", "astaD", "asta2D", "orejaI", "astaI", "asta2I", "musloDD", "canaDD", "musloDI", "canaDI", "musloTD", "canaTD", "musloTI", "canaTI", "cola"};
    static final int[] PADRES_CIERVO = {-1, 0, 1, 2, 2, 4, 2, 2, 7, 0, 9, 0, 11, 0, 13, 0, 15, 0};
    // {hueso, k, c, g, peso, puntaX, puntaY, puntaZ, limite}
    static final float[][] RESORTES_CIERVO = {{3, 160f, 7f, 7f, 0f, -1f, 0f, 0f, 0.5f}, {4, 240f, 10f, 4f, 0f, 0f, -1f, 0f, 0.25f}, {5, 200f, 8f, 5f, 0f, 0f, -1f, 0f, 0.35f}, {6, 160f, 7f, 7f, 0f, 1f, 0f, 0f, 0.5f}, {7, 240f, 10f, 4f, 0f, 0f, -1f, 0f, 0.25f}, {8, 200f, 8f, 5f, 0f, 0f, -1f, 0f, 0.35f}, {17, 90f, 5f, 8f, 0.2f, 0f, 0.9578f, 0.2873f, 0.8f}};
    // {hueso, x, y, z, ancho}  (punto en pixeles del hueso)
    static final float[][] ESTELAS_CIERVO = {{2, 0f, -1f, -7f, 0.1f}, {14, 0f, 8f, 0f, 0.08f}};
    static final int ESTELA_CIERVO = 0x9FE9FF;
    static final boolean TRANSLUCIDO_CIERVO = true;
    static final float ESCALA_CIERVO = 1.15f;

    static final String[] HUESOS_ESCORPION = {"cuerpo", "pataD0", "patabD0", "pataD1", "patabD1", "pataD2", "patabD2", "pataD3", "patabD3", "brazoD", "anteD", "pinzaD", "dedoD", "pataI0", "patabI0", "pataI1", "patabI1", "pataI2", "patabI2", "pataI3", "patabI3", "brazoI", "anteI", "pinzaI", "dedoI", "cola0", "cola1", "cola2", "cola3", "cola4", "cola5", "aguijon"};
    static final int[] PADRES_ESCORPION = {-1, 0, 1, 0, 3, 0, 5, 0, 7, 0, 9, 10, 11, 0, 13, 0, 15, 0, 17, 0, 19, 0, 21, 22, 23, 0, 25, 26, 27, 28, 29, 30};
    // {hueso, k, c, g, peso, puntaX, puntaY, puntaZ, limite}
    static final float[][] RESORTES_ESCORPION = {{25, 150f, 9f, 5f, 0.15f, 0f, 0f, 1f, 0.45f}, {26, 138f, 9f, 5.8f, 0.15f, 0f, 0f, 1f, 0.45f}, {27, 126f, 9f, 6.6f, 0.15f, 0f, 0f, 1f, 0.45f}, {28, 114f, 9f, 7.4f, 0.15f, 0f, 0f, 1f, 0.45f}, {29, 102f, 9f, 8.2f, 0.15f, 0f, 0f, 1f, 0.45f}, {30, 90f, 9f, 9f, 0.15f, 0f, 0f, 1f, 0.45f}, {31, 120f, 7f, 7f, 0f, 0f, 0f, 1f, 0.5f}};
    // {hueso, x, y, z, ancho}  (punto en pixeles del hueso)
    static final float[][] ESTELAS_ESCORPION = {{31, 0f, 2f, 4.5f, 0.1f}, {11, 0f, 0f, -7f, 0.09f}, {23, 0f, 0f, -7f, 0.09f}};
    static final int ESTELA_ESCORPION = 0xFFC46A;
    static final boolean TRANSLUCIDO_ESCORPION = false;
    static final float ESCALA_ESCORPION = 1.3f;

    static final String[] HUESOS_YETI = {"cadera", "torso", "cabeza", "mandibula", "brazoD", "anteD", "manoD", "musloD", "piernaD", "mechHombroD", "mechCodoD", "brazoI", "anteI", "manoI", "musloI", "piernaI", "mechHombroI", "mechCodoI", "mechEsp0", "mechEsp1", "mechEsp2"};
    static final int[] PADRES_YETI = {-1, 0, 1, 2, 1, 4, 5, 0, 7, 1, 5, 1, 11, 12, 0, 14, 1, 12, 1, 1, 1};
    // {hueso, k, c, g, peso, puntaX, puntaY, puntaZ, limite}
    static final float[][] RESORTES_YETI = {{9, 110f, 7f, 7f, 0.3f, 0f, 1f, 0f, 0.6f}, {10, 90f, 6f, 8f, 0.4f, 0f, 1f, 0f, 0.7f}, {16, 110f, 7f, 7f, 0.3f, 0f, 1f, 0f, 0.6f}, {17, 90f, 6f, 8f, 0.4f, 0f, 1f, 0f, 0.7f}, {18, 100f, 6f, 8f, 0.3f, 0f, 1f, 0f, 0.6f}, {19, 100f, 6f, 8f, 0.3f, 0f, 1f, 0f, 0.6f}, {20, 100f, 6f, 8f, 0.3f, 0f, 1f, 0f, 0.6f}};
    // {hueso, x, y, z, ancho}  (punto en pixeles del hueso)
    static final float[][] ESTELAS_YETI = {{6, 0f, 3f, 0f, 0.14f}, {13, 0f, 3f, 0f, 0.14f}};
    static final int ESTELA_YETI = 0xBFE9FF;
    static final boolean TRANSLUCIDO_YETI = false;
    static final float ESCALA_YETI = 1.35f;

    static final String[] HUESOS_SAPO = {"cuerpo", "panza", "cabeza", "ojoD", "ojoI", "mandibula", "buche", "lengua", "punta", "musloTD", "canaTD", "pieTD", "brazoD", "manoDD", "musloTI", "canaTI", "pieTI", "brazoI", "manoDI"};
    static final int[] PADRES_SAPO = {-1, 0, 0, 2, 2, 2, 5, 2, 7, 0, 9, 10, 0, 12, 0, 14, 15, 0, 17};
    // {hueso, k, c, g, peso, puntaX, puntaY, puntaZ, limite}
    static final float[][] RESORTES_SAPO = {{1, 140f, 6f, 4f, 0f, 0f, 1f, 0f, 0.25f}, {3, 220f, 6f, 4f, 0f, 0f, -1f, 0f, 0.3f}, {4, 220f, 6f, 4f, 0f, 0f, -1f, 0f, 0.3f}};
    // {hueso, x, y, z, ancho}  (punto en pixeles del hueso)
    static final float[][] ESTELAS_SAPO = {{8, 0f, 0f, -1f, 0.08f}};
    static final int ESTELA_SAPO = 0xFF9AB0;
    static final boolean TRANSLUCIDO_SAPO = false;
    static final float ESCALA_SAPO = 1f;

    static final String[] HUESOS_GOLEM = {"cadera", "torso", "cabeza", "brazoD", "anteD", "punoD", "raizBrD0", "raizBrD1", "raizBrD2", "raizBrD3", "musloD", "piernaD", "brazoI", "anteI", "punoI", "raizBrI0", "raizBrI1", "raizBrI2", "raizBrI3", "musloI", "piernaI", "raizC0", "raizC1", "raizC2", "raizC3", "raizC20", "raizC21", "raizC22", "raizC23", "raizE0", "raizE1", "raizE2", "raizE3", "raizE20", "raizE21", "raizE22", "raizE23"};
    static final int[] PADRES_GOLEM = {-1, 0, 1, 1, 3, 4, 4, 6, 7, 8, 0, 10, 1, 12, 13, 13, 15, 16, 17, 0, 19, 0, 21, 22, 23, 0, 25, 26, 27, 1, 29, 30, 31, 1, 33, 34, 35};
    // {hueso, k, c, g, peso, puntaX, puntaY, puntaZ, limite}
    static final float[][] RESORTES_GOLEM = {{6, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {7, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {8, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {9, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {15, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {16, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {17, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {18, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {21, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {22, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {23, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {24, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {25, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {26, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {27, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {28, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {29, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {30, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {31, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {32, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {33, 55f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {34, 60f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {35, 65f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}, {36, 70f, 4.5f, 9f, 0.9f, 0f, 1f, 0f, 1.1f}};
    // {hueso, x, y, z, ancho}  (punto en pixeles del hueso)
    static final float[][] ESTELAS_GOLEM = {{5, 0f, 6f, 0f, 0.22f}, {14, 0f, 6f, 0f, 0.22f}};
    static final int ESTELA_GOLEM = 0xFFB347;
    static final boolean TRANSLUCIDO_GOLEM = false;
    static final float ESCALA_GOLEM = 1.75f;

}
