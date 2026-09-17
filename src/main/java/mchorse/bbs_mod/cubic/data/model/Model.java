package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Model implements IMapSerializable, IModel
{
    public int textureWidth;
    public int textureHeight;

    public final MolangParser parser;

    /**
     * This list contains only the root groups of the model (and not all of the groups)
     */
    public List<ModelGroup> topGroups = new ArrayList<>();

    /** The welds between the model's cubes. One whose cube left the model is dropped when the model is next settled ({@link #initialize()}). */
    public final List<ModelWeld> welds = new ArrayList<>();

    private Map<String, ModelGroup> namedGroups = new HashMap<>();
    private List<ModelGroup> orderedGroups = new ArrayList<>();
    private Set<String> shapeKeys = new HashSet<>();
    private int nextIndex;

    public Model(MolangParser parser)
    {
        this.parser = parser;
    }

    /**
     * Replace the groups with the ones in {@code data} — a snapshot from {@link #toData()}, the way
     * the model editor's undo keeps them — and settle the hierarchy again. Every group object is a
     * new one: whatever held one now holds a dead one.
     */
    public void reload(MapType data)
    {
        this.topGroups.clear();
        this.fromData(data);
        this.initialize();
    }

    public void initialize()
    {
        this.nextIndex = 0;
        this.namedGroups.clear();
        this.orderedGroups.clear();
        this.shapeKeys.clear();

        this.fillGroups(this.topGroups, null);

        for (ModelGroup orderedGroup : this.orderedGroups)
        {
            for (ModelMesh mesh : orderedGroup.meshes)
            {
                this.shapeKeys.addAll(mesh.data.keySet());
            }
        }

        this.dropDeadWelds();
    }

    /** Forget the welds one of whose cubes is no longer in the model — removed, or gone along with its group. */
    private void dropDeadWelds()
    {
        if (this.welds.isEmpty())
        {
            return;
        }

        Set<ModelCube> cubes = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ModelGroup group : this.orderedGroups)
        {
            cubes.addAll(group.cubes);
        }

        this.welds.removeIf((weld) -> !cubes.contains(weld.sourceCube) || !cubes.contains(weld.targetCube));
    }

    private void fillGroups(List<ModelGroup> groups, ModelGroup parent)
    {
        for (ModelGroup group : groups)
        {
            this.namedGroups.put(group.id, group);
            this.orderedGroups.add(group);

            group.parent = parent;
            group.owner = this;
            group.index = this.nextIndex;
            this.nextIndex += 1;

            this.fillGroups(group.children, group);
        }
    }

    public List<ModelGroup> getOrderedGroups()
    {
        return this.orderedGroups;
    }

    @Override
    public Collection<? extends RigBone> getRigBones()
    {
        return this.orderedGroups;
    }

    @Override
    public RigBone getBone(String name)
    {
        return this.getGroup(name);
    }

    public ModelGroup getGroup(String id)
    {
        return this.namedGroups.get(id);
    }

    /** The group the cube belongs to, or null for a cube that isn't in the model. */
    public ModelGroup findGroup(ModelCube cube)
    {
        for (ModelGroup group : this.orderedGroups)
        {
            for (ModelCube other : group.cubes)
            {
                if (other == cube)
                {
                    return group;
                }
            }
        }

        return null;
    }

    /** Rebuild what these groups' cubes draw as, after their numbers changed. */
    public void refreshGeometry(Collection<ModelGroup> groups)
    {
        for (ModelGroup group : groups)
        {
            group.generateQuads(this.textureWidth, this.textureHeight);
        }
    }

    /** Rebuild what every cube of the model draws as — what a change to the unwrap's sheet needs. */
    public void regenerateQuads()
    {
        this.refreshGeometry(this.orderedGroups);
    }

    /**
     * The size of the sheet every face's unwrap is measured against — the {@code texture} of the
     * file, not the size of the PNG. Changing it re-reads every cube's unwrap against the new one,
     * so the same numbers cover a different share of the sheet.
     */
    public void setTextureSize(int width, int height)
    {
        this.textureWidth = width;
        this.textureHeight = height;

        this.regenerateQuads();
    }

    /**
     * Move a group and everything under it by {@code delta}, in the model's own units: the pivot it
     * rests at, the cubes and meshes it carries, and the same for every group below it. A cubic
     * model's geometry is absolute — a group's cubes stand where the file puts them, and the
     * hierarchy passes down rotations alone — so moving a group's geometry means moving its whole
     * subtree's along with it.
     *
     * <p>Only the numbers move here. The quads the cubes draw as are rebuilt by
     * {@link #refreshGeometry(Collection)} over {@link #collectSubtree}, once the numbers of a
     * gesture have settled, rather than on every sample of a drag.</p>
     */
    public void shiftGroup(ModelGroup group, Vector3f delta)
    {
        group.initial.translate.add(delta);

        /* The pose rides along, so the bone doesn't jump by the delta over the frames between here
         * and the next reset — which copies the rest into it again. */
        group.current.translate.add(delta);

        for (ModelCube cube : group.cubes)
        {
            cube.shift(delta);
        }

        /* A mesh moves by its vertices alone, and its origin — the point it turns about — stays.
         * ModelMesh reads its vertices relative to that origin and writes them back absolute, so an
         * origin this editor moved off zero would shift the mesh again on every reload; the pivot of
         * a mesh is left to whatever authored it. */
        for (ModelMesh mesh : group.meshes)
        {
            for (Vector3f vertex : mesh.baseData.vertices)
            {
                vertex.add(delta);
            }

            for (ModelData shapeKey : mesh.data.values())
            {
                for (Vector3f vertex : shapeKey.vertices)
                {
                    vertex.add(delta);
                }
            }
        }

        for (ModelGroup child : group.children)
        {
            this.shiftGroup(child, delta);
        }
    }

    /** A group and every group under it, parents before children. */
    public List<ModelGroup> collectSubtree(ModelGroup group, List<ModelGroup> out)
    {
        out.add(group);

        for (ModelGroup child : group.children)
        {
            this.collectSubtree(child, out);
        }

        return out;
    }

    /* IModel implementation */

    @Override
    public Pose createPose()
    {
        Pose pose = new Pose();

        for (String key : this.getAllGroupKeys())
        {
            PoseTransform poseTransform = pose.getOrCreate(key);
            ModelGroup group = this.getGroup(key);

            poseTransform.copy(group.current);
            poseTransform.translate.sub(group.initial.translate);
            poseTransform.rotate.sub(group.initial.rotate);

            poseTransform.rotate.x = MathUtils.toRad(poseTransform.rotate.x);
            poseTransform.rotate.y = MathUtils.toRad(poseTransform.rotate.y);
            poseTransform.rotate.z = MathUtils.toRad(poseTransform.rotate.z);
        }

        return pose;
    }

    @Override
    public void resetPose()
    {
        for (ModelGroup orderedGroup : this.orderedGroups)
        {
            orderedGroup.reset();
        }
    }

    /** Record every group's channels-phase orient/offset — see {@link ModelGroup#snapshotChannels()}. */
    @Override
    public void snapshotChannels()
    {
        for (ModelGroup orderedGroup : this.orderedGroups)
        {
            orderedGroup.snapshotChannels();
        }
    }

    /** Rewind every group's orient/offset to the channels-phase snapshot. */
    @Override
    public void restoreChannels()
    {
        for (ModelGroup orderedGroup : this.orderedGroups)
        {
            orderedGroup.restoreChannels();
        }
    }

    @Override
    public void applyPose(Pose pose)
    {
        if (pose.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform transform = entry.getValue();
            ModelGroup group = this.getGroup(entry.getKey());

            if (group == null)
            {
                continue;
            }

            if (transform.fix > 0F)
            {
                group.current.lerp(group.initial, transform.fix);

                /* fix blends toward the bind pose, so any composed orientation from earlier layers no longer
                 * applies — drop it and let composeOrient below re-seed from the fix-lerped euler. */
                group.orient = null;
            }

            group.lighting = transform.lighting;
            group.poseVisible &= transform.visible;
            group.color.copy(transform.color);
            group.overlay.copy(transform.overlay);
            group.current.translate.add(transform.translate);
            group.current.scale.add(transform.scale).sub(1, 1, 1);

            if (transform.rotationMode == Transform.RotationMode.QUATERNION)
            {
                /* Quaternion pose: seed orient from the euler accumulated so far
                 * (rest + prior layers) if needed, then compose the pose quaternion
                 * straight in — no euler decomposition, so the render stays gimbal-
                 * free. The euler readback into current.rotate is only kept for the
                 * gizmo/IK sampling, not the render (which follows orient). */
                if (group.orient == null)
                {
                    group.orient = Matrices.toLocalRotationZYXDegrees(group.current.rotate);
                }

                group.orient.mul(transform.createRotation());

                Vector3f euler = Matrices.toEulerZYXRadians(transform.quat, new Vector3f());

                group.current.rotate.add(
                    (float) Math.toDegrees(euler.x),
                    (float) Math.toDegrees(euler.y),
                    (float) Math.toDegrees(euler.z)
                );
            }
            else
            {
                group.current.rotate.add(
                    (float) Math.toDegrees(transform.rotate.x),
                    (float) Math.toDegrees(transform.rotate.y),
                    (float) Math.toDegrees(transform.rotate.z)
                );

                /* Compose the pose rotation into the orientation quaternion.
                 * The euler readback above is kept for gizmo/IK; orient is the render truth past the first layer. */
                group.composeOrient(Matrices.toQuaternionZYXDegrees(
                    (float) Math.toDegrees(transform.rotate.x),
                    (float) Math.toDegrees(transform.rotate.y),
                    (float) Math.toDegrees(transform.rotate.z)
                ));
            }
        }
    }

    @Override
    public Set<String> getShapeKeys()
    {
        return this.shapeKeys;
    }

    @Override
    public String getAnchor()
    {
        return !this.topGroups.isEmpty() ? this.topGroups.get(0).id : "";
    }

    @Override
    public Set<String> getAllGroupKeys()
    {
        return this.namedGroups.keySet();
    }

    @Override
    public Collection<String> getAllChildrenKeys(String key)
    {
        ModelGroup group = this.namedGroups.get(key);
        List<String> groups = new ArrayList<>();

        this.collectChildrenKeys(group, groups);

        return groups;
    }

    private void collectChildrenKeys(ModelGroup group, List<String> groups)
    {
        for (ModelGroup child : group.children)
        {
            groups.add(child.id);
            this.collectChildrenKeys(child, groups);
        }
    }

    @Override
    public Collection<ModelGroup> getAllGroups()
    {
        return this.namedGroups.values();
    }

    @Override
    public Collection<BOBJBone> getAllBOBJBones()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getAdjacentGroups(String groupName)
    {
        ModelGroup group = this.getGroup(groupName);
        List<ModelGroup> groups = group.parent != null ? group.parent.children : this.topGroups;

        return groups.stream().map((g) -> g.id).toList();
    }

    @Override
    public Collection<String> getHierarchyGroups(String groupName)
    {
        ModelGroup group = this.getGroup(groupName);
        List<String> groups = new ArrayList<>();

        while (group != null)
        {
            groups.add(group.id);

            group = group.parent;
        }

        return groups;
    }

    @Override
    public Collection<String> getRootGroupKeys()
    {
        return this.topGroups.stream().map((g) -> g.id).toList();
    }

    @Override
    public Collection<String> getDirectChildrenKeys(String key)
    {
        ModelGroup group = this.getGroup(key);

        if (group == null)
        {
            return Collections.emptyList();
        }

        return group.children.stream().map((g) -> g.id).toList();
    }

    @Override
    public String getParentGroupKey(String key)
    {
        ModelGroup group = this.getGroup(key);

        return group == null || group.parent == null ? "" : group.parent.id;
    }

    @Override
    public void apply(IEntity target, Animation action, float tick, float blend, float transition, boolean skipInitial)
    {
        MolangHelper.setMolangVariables(this.parser, target, tick, transition);
        CubicModelAnimator.animate(this, action, tick, blend, skipInitial);
    }

    @Override
    public void postApply(IEntity target, Animation action, float tick, float transition)
    {
        MolangHelper.setMolangVariables(this.parser, target, tick, transition);
        CubicModelAnimator.postAnimate(this, action, tick);
    }

    /* Deserialization / Serialization */

    @Override
    public void fromData(MapType data)
    {
        ListType texture = data.getList("texture");

        this.textureWidth = texture.getInt(0);
        this.textureHeight = texture.getInt(1);

        MapType groups = data.getMap("groups");
        Map<String, List<String>> hierarchy = new HashMap<>();
        Map<String, ModelGroup> flatGroups = new HashMap<>();

        for (String key : groups.keys())
        {
            MapType groupElement = groups.getMap(key);
            ModelGroup group = new ModelGroup(key);

            /* Fill hierarchy information */
            String parent = groupElement.has("parent") ? groupElement.getString("parent") : "";
            List<String> list = hierarchy.computeIfAbsent(parent, (k) -> new ArrayList<>());

            list.add(group.id);

            group.fromData(groupElement);

            for (ModelCube cube : group.cubes)
            {
                cube.generateQuads(this.textureWidth, this.textureHeight);
            }

            flatGroups.put(group.id, group);
        }

        /* Setup hierarchy */
        for (Map.Entry<String, List<String>> entry : hierarchy.entrySet())
        {
            if (entry.getKey().isEmpty())
            {
                continue;
            }

            ModelGroup group = flatGroups.get(entry.getKey());

            for (String child : entry.getValue())
            {
                group.children.add(flatGroups.get(child));
            }
        }

        List<String> topLevel = hierarchy.get("");

        if (topLevel != null)
        {
            for (String rootGroup : topLevel)
            {
                this.topGroups.add(flatGroups.get(rootGroup));
            }
        }

        /* By the groups just read: the model's own map of them is only rebuilt by initialize(), and
         * until then it holds the groups of before — or none, on a first load. */
        this.readWelds(data.getList("welds"), flatGroups);
    }

    /** The file's welds, bound to the cubes just read; an entry naming a cube or a side that isn't there is left out. */
    private void readWelds(ListType list, Map<String, ModelGroup> groups)
    {
        this.welds.clear();

        for (BaseType element : list)
        {
            if (!element.isMap())
            {
                continue;
            }

            MapType entry = element.asMap();
            MapType source = entry.getMap("source");
            MapType target = entry.getMap("target");
            ModelCube sourceCube = cubeAt(groups, source);
            ModelCube targetCube = cubeAt(groups, target);
            CubeFace sourceFace = CubeFace.fromName(source.getString("face"));
            CubeFace targetFace = CubeFace.fromName(target.getString("face"));

            if (sourceCube == null || targetCube == null || sourceFace == null || targetFace == null)
            {
                continue;
            }

            ModelWeld weld = new ModelWeld(sourceCube, sourceFace, targetCube, targetFace);

            weld.settingsFromData(entry);
            this.welds.add(weld);
        }
    }

    /** The cube a weld's side names by its group and its place there, or null when there's none. */
    private static ModelCube cubeAt(Map<String, ModelGroup> groups, MapType side)
    {
        ModelGroup group = groups.get(side.getString("group"));
        int index = side.getInt("cube", -1);

        return group != null && index >= 0 && index < group.cubes.size() ? group.cubes.get(index) : null;
    }

    @Override
    public void toData(MapType data)
    {
        ListType texture = new ListType();

        texture.addInt(this.textureWidth);
        texture.addInt(this.textureHeight);

        /* The groups go out in tree order — parents before children, siblings as they stand — into an
         * ordered map: the file's order is the order they come back in, so a save must not shuffle
         * the tree. */
        MapType groups = new MapType(false);
        Map<ModelCube, CubeAddress> addresses = new IdentityHashMap<>();

        this.writeGroups(this.topGroups, null, groups, addresses);

        data.put("texture", texture);
        data.put("groups", groups);

        /* Only when there are any: whatever reads models without welds — an older BBS, a model
         * exported off a form — has nothing new to skip. */
        ListType welds = this.writeWelds(addresses);

        if (!welds.isEmpty())
        {
            data.put("welds", welds);
        }
    }

    /**
     * The groups, and where each cube of theirs is written — by this very walk rather than by the
     * model's own maps, which an edit in progress hasn't settled yet ({@link #initialize()}).
     */
    private void writeGroups(List<ModelGroup> list, ModelGroup parent, MapType groups, Map<ModelCube, CubeAddress> addresses)
    {
        for (ModelGroup group : list)
        {
            MapType groupData = group.toData();

            if (parent != null)
            {
                groupData.putString("parent", parent.id);
            }

            for (int i = 0; i < group.cubes.size(); i++)
            {
                addresses.put(group.cubes.get(i), new CubeAddress(group.id, i));
            }

            groups.put(group.id, groupData);
            this.writeGroups(group.children, group, groups, addresses);
        }
    }

    /** The welds, each side by its cube's address; a weld whose cube was just taken out of the model isn't written. */
    private ListType writeWelds(Map<ModelCube, CubeAddress> addresses)
    {
        ListType list = new ListType();

        for (ModelWeld weld : this.welds)
        {
            CubeAddress source = addresses.get(weld.sourceCube);
            CubeAddress target = addresses.get(weld.targetCube);

            if (source == null || target == null)
            {
                continue;
            }

            MapType entry = new MapType(false);

            entry.put("source", source.toData(weld.sourceFace));
            entry.put("target", target.toData(weld.targetFace));
            weld.settingsToData(entry);
            list.add(entry);
        }

        return list;
    }

    /** Where a cube is written: its group and its place among the group's cubes. */
    private record CubeAddress(String group, int cube)
    {
        /** A weld's side at this cube. */
        public MapType toData(CubeFace face)
        {
            MapType side = new MapType(false);

            side.putString("group", this.group);
            side.putInt("cube", this.cube);
            side.putString("face", ModelCube.faceKey(face));

            return side;
        }
    }
}


