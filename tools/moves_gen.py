import json, math
JOINTS=['head','neck','hip','le','lh','re','rh','lk','lf','lt','rk','rf','rt']
def P(x,y): return [round(x,1),round(y,1)]
def fk(t=0, hd=0, la=(5,5), ra=(5,5), ll=(0,0,90), rl=(0,0,90), hip=(50,50), front=False):
    """Pose por ángulos (grados). Torso t: 0 = recto, 90 = tumbado mirando a la derecha.
    Brazos/piernas: 0 = hacia abajo, 90 = hacia delante (derecha), 180 = arriba, -90 = atrás."""
    s=lambda a: math.sin(math.radians(a)); c=lambda a: math.cos(math.radians(a))
    hx,hy=hip
    neck=(hx+28*s(t), hy-28*c(t)); head=(neck[0]+9*s(t+hd), neck[1]-9*c(t+hd))
    sh=(hx+25*s(t), hy-25*c(t))
    # De frente: hombros y caderas separados para que se vea el cuerpo de frente
    ls=(sh[0]-7,sh[1]) if front else sh; rs=(sh[0]+7,sh[1]) if front else sh
    lp=(hx-4,hy) if front else hip; rp=(hx+4,hy) if front else hip
    def arm(o,a): e=(o[0]+13*s(a[0]), o[1]+13*c(a[0])); h=(e[0]+12*s(a[1]), e[1]+12*c(a[1])); return e,h
    def leg(o,l):
        k=(o[0]+18*s(l[0]), o[1]+18*c(l[0])); f=(k[0]+18*s(l[1]), k[1]+18*c(l[1]))
        to=(f[0]+6*s(l[2]), f[1]+6*c(l[2])); return k,f,to
    le,lh=arm(ls,la); re,rh=arm(rs,ra); lk,lf,lt=leg(lp,ll); rk,rf,rt=leg(rp,rl)
    d=dict(head=head,neck=neck,hip=hip,le=le,lh=lh,re=re,rh=rh,lk=lk,lf=lf,lt=lt,rk=rk,rf=rf,rt=rt)
    if front: d.update(ls=ls,rs=rs,lp=lp,rp=rp)
    return d
def ground(frames, ax=None, groundy=90, xs=50):
    """Apoya cada fotograma en el suelo y fija la x (de un punto o del centro del 1er fotograma)."""
    out=[]
    for i,f in enumerate(frames):
        maxy=max(p[1] for p in f.values()); dy=groundy-maxy
        f={k:(v[0],v[1]+dy) for k,v in f.items()}
        out.append(f)
    if ax:
        x0=out[0][ax][0]
        out=[{k:(v[0]-f[ax][0]+x0,v[1]) for k,v in f.items()} for f in out]
    xsAll=[p[0] for f in out for p in f.values()]; cx=(min(xsAll)+max(xsAll))/2
    return [{k:P(v[0]-cx+xs,v[1]) for k,v in f.items()} for f in out]
def ex(**pts):  # pose explícita
    return {k:tuple(v) for k,v in pts.items()}

A={}
stand=dict(la=(4,4),ra=(-4,-2))
# --- sentadilla
A['squat']=dict(dur=2.6, frames=ground([fk(**stand), fk(t=40,la=(95,92),ra=(95,92),ll=(95,-25,90),rl=(95,-25,90))],'rt'))
A['chairsquat']=dict(dur=3.0, frames=ground([fk(**stand), fk(t=30,la=(80,85),ra=(80,85),ll=(88,-5,90),rl=(88,-5,90))],'rt'), prop='chair')
# --- zancada
A['lunge']=dict(dur=3.0, frames=ground([fk(la=(4,4),ra=(4,4),ll=(-12,-12,90),rl=(12,12,90)),
    fk(la=(4,4),ra=(4,4),ll=(-12,-78,130),rl=(80,0,90)), fk(la=(4,4),ra=(4,4),ll=(-12,-12,90),rl=(12,12,90)),
    fk(la=(4,4),ra=(4,4),ll=(80,0,90),rl=(-12,-78,130))],'hip'))
# --- rodillas arriba
A['highknees']=dict(dur=1.2, frames=ground([fk(la=(-30,40),ra=(40,110),ll=(95,0,90),rl=(0,0,90)), fk(la=(40,110),ra=(-30,40),ll=(0,0,90),rl=(95,0,90))],'hip'))
# --- caminar / correr / escaleras
A['walk']=dict(dur=1.2, frames=ground([fk(t=4,la=(-25,-10),ra=(25,45),ll=(25,10,90),rl=(-22,-35,95)),
    fk(t=4,la=(0,10),ra=(0,10),ll=(5,25,90),rl=(-5,-5,90)),
    fk(t=4,la=(25,45),ra=(-25,-10),ll=(-22,-35,95),rl=(25,10,90)),
    fk(t=4,la=(0,10),ra=(0,10),ll=(-5,-5,90),rl=(5,25,90))],'hip'))
A['run']=dict(dur=0.8, frames=ground([fk(t=12,la=(-45,40),ra=(50,130),ll=(55,-10,95),rl=(-35,-100,150)),
    fk(t=12,la=(50,130),ra=(-45,40),ll=(-35,-100,150),rl=(55,-10,95))],'hip'))
A['stairs']=dict(dur=1.4, frames=ground([fk(t=10,la=(-25,0),ra=(30,60),ll=(75,0,90),rl=(-5,-5,90)),
    fk(t=10,la=(30,60),ra=(-25,0),ll=(-5,-5,90),rl=(75,0,90))],'hip'), prop='stairs')
# --- talones
A['calfraise']=dict(dur=2.0, frames=ground([fk(la=(-30,40),ra=(-30,40)), fk(la=(-30,40),ra=(-30,40),ll=(0,0,30),rl=(0,0,30))],'rt'))
A['calfstretch']=dict(dur=3.0, frames=ground([fk(t=25,la=(80,80),ra=(80,80),ll=(-35,-35,55),rl=(30,0,90)), fk(t=30,la=(85,85),ra=(85,85),ll=(-38,-38,52),rl=(35,0,90))],'rt'), prop='wall')
# --- de frente: tijeras
A['jacks']=dict(dur=1.0, frames=ground([fk(front=True,la=(-8,-8),ra=(8,8),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,la=(-150,-170),ra=(150,170),ll=(-22,-22,-90),rl=(22,22,90))],'hip'))
A['dance']=dict(dur=1.6, frames=ground([fk(front=True,t=-8,hd=-10,la=(-140,-170),ra=(60,140),ll=(-15,-5,-90),rl=(10,30,90)),
    fk(front=True,t=8,hd=10,la=(-60,-140),ra=(140,170),ll=(-10,-30,-90),rl=(15,5,90))],'hip'))
A['breathe']=dict(dur=8.0, frames=ground([fk(front=True,la=(-10,-10),ra=(10,10),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,hd=0,la=(-100,-110),ra=(100,110),ll=(-4,-4,-90),rl=(4,4,90))],'hip'))
A['neck']=dict(dur=4.0, frames=ground([fk(front=True,hd=-55,la=(-8,-8),ra=(8,8),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,hd=0,la=(-8,-8),ra=(8,8),ll=(-4,-4,-90),rl=(4,4,90)),
    fk(front=True,hd=55,la=(-8,-8),ra=(8,8),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,hd=0,la=(-8,-8),ra=(8,8),ll=(-4,-4,-90),rl=(4,4,90))],'hip'))
A['chestopen']=dict(dur=3.0, frames=ground([fk(front=True,la=(-10,-10),ra=(10,10),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,hd=-8,la=(-50,-10),ra=(50,10),ll=(-4,-4,-90),rl=(4,4,90))],'hip'))
A['shoulders']=dict(dur=2.4, frames=ground([fk(front=True,la=(-90,-90),ra=(90,90),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,la=(-110,-110),ra=(110,110),ll=(-4,-4,-90),rl=(4,4,90)),
    fk(front=True,la=(-90,-90),ra=(90,90),ll=(-4,-4,-90),rl=(4,4,90)), fk(front=True,la=(-70,-70),ra=(70,70),ll=(-4,-4,-90),rl=(4,4,90))],'hip'))
# --- sentada (de lado): muñecas, torsión, talones sentada
seat=dict(ll=(90,0,90),rl=(90,0,90))
A['wrists']=dict(dur=1.6, frames=ground([fk(la=(40,110),ra=(40,110),**seat), fk(la=(40,70),ra=(40,70),**seat)],'hip'), prop='chair_seated')
A['twist']=dict(dur=4.0, frames=ground([fk(hd=0,la=(10,60),ra=(-20,60),**seat), fk(hd=-30,la=(-30,-10),ra=(-60,-20),**seat), fk(hd=0,la=(10,60),ra=(-20,60),**seat), fk(hd=30,la=(60,40),ra=(30,20),**seat)],'hip'), prop='chair_seated')
A['seatedheels']=dict(dur=1.6, frames=ground([fk(la=(40,80),ra=(40,80),**seat), fk(la=(40,80),ra=(40,80),ll=(80,-8,150),rl=(80,-8,150))],'hip'), prop='chair_seated')
# --- en el suelo (poses explícitas, de lado mirando a la derecha; suelo y=90)
def plank(lift=0):
    return ex(lt=(14,90),lf=(16,89),rt=(14,90),rf=(16,89),lk=(30,86-lift*.5),rk=(30,86-lift*.5),hip=(46,82-lift),neck=(70,77),head=(79,74),
              le=(70,90),lh=(82,90),re=(70,90),rh=(82,90))
A['plank']=dict(dur=3.0, frames=ground([plank(0),plank(2)]))
def push(down):
    sy=66+14*down
    return ex(lt=(14,90),lf=(16,89),rt=(14,90),rf=(16,89),lk=(31,85-9*(1-down)*.6),rk=(31,85-9*(1-down)*.6),hip=(45,79-6*(1-down)) if True else None,
              neck=(70,sy),head=(79,sy-4),le=(70-9*down,sy+12-1*down),lh=(71,90),re=(70-9*down,sy+12-1*down),rh=(71,90))
A['pushup']=dict(dur=2.2, frames=ground([push(0),push(1)]))
def incline(down):
    sy=52+8*down
    return ex(lt=(14,90),lf=(16,89),rt=(14,90),rf=(16,89),lk=(28,80-2*down),rk=(28,80-2*down),hip=(42,70-1*down),neck=(68,sy),head=(76,sy-5),
              le=(68-7*down,sy+10),lh=(74,72),re=(68-7*down,sy+10),rh=(74,72))
A['inclinepush']=dict(dur=2.4, frames=ground([incline(0),incline(1)]), prop='bench')
def bridge(up):
    hy=86-12*up
    return ex(head=(14,84),neck=(22,86),hip=(46,hy),le=(32,90),lh=(42,90),re=(32,90),rh=(42,90),
              lk=(64,70-2*up),rk=(64,70-2*up),lf=(70,89),rf=(70,89),lt=(76,90),rt=(76,90))
A['bridge']=dict(dur=2.6, frames=ground([bridge(0),bridge(1)]))
def catcow(cat):
    by=58-6*cat
    return ex(lk=(30,90),rk=(30,90),lf=(14,90),rf=(14,90),lt=(10,90),rt=(10,90),hip=(32,64),neck=(66,64),head=(74,64+8*cat-5*(1-cat)),
              le=(68,77),lh=(68,90),re=(68,77),rh=(68,90),spine=(49,by+10*(1-cat)-4))
A['catcow']=dict(dur=4.0, frames=ground([catcow(0),catcow(1)]))
json.dump(A, open('app/src/main/assets/moves.json','w'))
print(len(A),'animaciones')

# ---- Objetos (silla, escalón, pared, banco) como polilíneas genéricas
def chair(an):
    hip=[f['hip'] for f in an['frames']]; hx=min(h[0] for h in hip); hy=max(h[1] for h in hip)
    sx=round(hx-12,1); sy=round(hy+3,1)
    return [[[sx-2,sy-24],[sx,sy],[sx+16,sy]],[[sx,sy],[sx,90]],[[sx+14,sy],[sx+14,90]]]
for nm,an in A.items():
    p=an.pop('prop',None)
    if p in ('chair','chair_seated'): an['props']=chair(an)
    elif p=='stairs': an['props']=[[[52,90],[52,77],[70,77],[70,64],[92,64]]]
    elif p=='wall': an['props']=[[[92,18],[92,90]]]
    elif p=='bench': an['props']=[[[66,73],[90,73]],[[70,73],[70,90]],[[86,73],[86,90]]]
# Ejercicio → animación
MAP={'Sentadillas':'squat','Plancha':'plank','Saltos de tijera':'jacks','Flexiones':'pushup','Zancadas alternas':'lunge',
 'Puente de glúteo':'bridge','Rodillas arriba':'highknees','Gato-vaca':'catcow','Bailar una canción':'dance','Caminata rápida':'walk',
 'Subir escaleras / cuesta':'stairs','Trote suave':'run','Flexiones en banco':'inclinepush','Estiramiento de gemelos':'calfstretch',
 'Respirar y mirar lejos':'breathe','Subir escaleras':'stairs','Paseo por el pasillo':'walk','Sentadilla a la silla':'chairsquat',
 'Estiramiento de cuello':'neck','Apertura de pecho':'chestopen','Elevación de talones':'calfraise','Rotación de hombros':'shoulders',
 'Muñecas y dedos':'wrists','Torsión sentada':'twist','Talones arriba sentada':'seatedheels','Paseo al baño / fuente':'walk',
 'Respiración 4-4-4-4':'breathe'}
json.dump({'moves':A,'map':MAP}, open('app/src/main/assets/moves.json','w'), separators=(',',':'))
