import UIKit
import SceneKit
import AVFoundation

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let w=UIWindow(frame: UIScreen.main.bounds)
        w.rootViewController=GameViewController()
        w.makeKeyAndVisible()
        window=w
        return true
    }
}

// Standalone prototype independent of the Unity project. Native SceneKit graphics.
final class GameViewController: UIViewController {
    let scene=SCNScene()
    let gameView=SCNView(frame:.zero)
    let hero=SCNNode()
    let cameraNode=SCNNode()
    var police=[SCNNode]()
    var cars=[SCNNode]()
    var activeCar:SCNNode?
    var obstructions=[(Float,Float)]()
    var up:Float=0, side:Float=0, yaw:Float=0, speed:Float=0
    var heat=0.0, ammo=50, money=150, lock=0, drill=0
    var missionDone=false, arabic=false, stealth=false
    var last:CFTimeInterval=0
    let status=UILabel()
    let message=UILabel()
    var note="Reach the vault at the northeast bank"

    func cube(_ width:CGFloat,_ height:CGFloat,_ length:CGFloat,_ color:UIColor)->SCNNode {
        let geometry=SCNBox(width:width,height:height,length:length,chamferRadius:0.08)
        let material=SCNMaterial()
        material.diffuse.contents=color
        geometry.materials=[material]
        return SCNNode(geometry:geometry)
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        gameView.frame=view.bounds
        gameView.autoresizingMask=[.flexibleWidth,.flexibleHeight]
        gameView.scene=scene
        gameView.isPlaying=true
        gameView.preferredFramesPerSecond=60
        gameView.antialiasingMode = .multisampling4X
        view.addSubview(gameView)
        setupCity()
        setupHUD()
        let d=UserDefaults.standard
        if d.object(forKey:"cash") != nil { money=d.integer(forKey:"cash") }
        missionDone=d.bool(forKey:"done")
        let timer=CADisplayLink(target:self,selector:#selector(update(_:)))
        timer.preferredFramesPerSecond=60
        timer.add(to:.main,forMode:.common)
    }
    func setupCity(){
        scene.background.contents=UIColor(red:0.13,green:0.14,blue:0.17,alpha:1)
        scene.fogStartDistance=55
        scene.fogEndDistance=140
        scene.fogColor=UIColor(red:0.18,green:0.18,blue:0.19,alpha:1)
        let floor=cube(170,0.1,170,UIColor(red:0.25,green:0.24,blue:0.21,alpha:1))
        scene.rootNode.addChildNode(floor)
        for n in -4...4 {
            let road1=cube(7,0.12,164,.darkGray)
            road1.position.x=Float(n)*16
            scene.rootNode.addChildNode(road1)
            let road2=cube(164,0.13,7,.darkGray)
            road2.position.z=Float(n)*16
            scene.rootNode.addChildNode(road2)
        }
        for x in -4...3 {
            for z in -4...3 {
                let h=CGFloat(5+abs((x*7+z*11)%14))
                let shade=CGFloat(abs((x*9+z*5)%4))*0.035
                let building=cube(7,h,7,UIColor(red:0.30+shade,green:0.25+shade,blue:0.21+shade,alpha:1))
                building.position=SCNVector3(Float(x)*16+8,Float(h/2),Float(z)*16+8)
                scene.rootNode.addChildNode(building)
                obstructions.append((building.position.x,building.position.z))
                for level in 0..<min(5,Int(h/2)) {
                    let window=cube(1,0.9,0.05,UIColor(red:0.9,green:0.66,blue:0.33,alpha:1))
                    window.position=SCNVector3(building.position.x,Float(level)*2+1.6,building.position.z-3.55)
                    scene.rootNode.addChildNode(window)
                }
            }
        }
        let vault=cube(3,2.7,0.3,UIColor(red:0.75,green:0.57,blue:0.18,alpha:1))
        vault.position=SCNVector3(25,1.35,-19.4)
        scene.rootNode.addChildNode(vault)
        hero.geometry=SCNCapsule(capRadius:0.4,height:1.8)
        hero.geometry?.firstMaterial?.diffuse.contents=UIColor(red:0.12,green:0.26,blue:0.31,alpha:1)
        hero.position=SCNVector3(0,0.9,0)
        let hat=cube(0.85,0.16,0.65,.black)
        hat.position.y=0.98
        hero.addChildNode(hat)
        scene.rootNode.addChildNode(hero)
        for i in 0..<7 {
            let officer=SCNNode(geometry:SCNCapsule(capRadius:0.34,height:1.7))
            officer.geometry?.firstMaterial?.diffuse.contents=UIColor(red:0.15,green:0.18,blue:0.34,alpha:1)
            officer.position=SCNVector3(Float(i%4)*12-23,0.85,Float(i/4)*18-12)
            scene.rootNode.addChildNode(officer)
            police.append(officer)
        }
        for i in 0..<5 {
            let car=SCNNode()
            let body=cube(2.3,0.9,4.2,i%2==0 ? .systemRed : .systemGreen)
            body.position.y=0.7
            car.addChildNode(body)
            let top=cube(1.9,0.8,2,.darkGray)
            top.position.y=1.4
            car.addChildNode(top)
            for xx in [-1.1 as Float,1.1] {
                for zz in [-1.2 as Float,1.2] {
                    let wheel=SCNNode(geometry:SCNCylinder(radius:0.43,height:0.24))
                    wheel.geometry?.firstMaterial?.diffuse.contents=UIColor.black
                    wheel.eulerAngles.z=Float.pi/2
                    wheel.position=SCNVector3(xx,0.4,zz)
                    car.addChildNode(wheel)
                }
            }
            car.position=SCNVector3(Float(i*12-12),0,Float(i%2==0 ? -2 : 2))
            cars.append(car)
            scene.rootNode.addChildNode(car)
        }
        cameraNode.camera=SCNCamera()
        cameraNode.camera?.fieldOfView=62
        cameraNode.position=SCNVector3(0,16,20)
        cameraNode.eulerAngles.x = -0.72
        scene.rootNode.addChildNode(cameraNode)
        gameView.pointOfView=cameraNode
        let lamp=SCNLight()
        lamp.type = .directional
        lamp.intensity=1100
        lamp.castsShadow=true
        let lightNode=SCNNode()
        lightNode.light=lamp
        lightNode.eulerAngles=SCNVector3(-0.9,0.6,0)
        scene.rootNode.addChildNode(lightNode)
        let ambient=SCNLight()
        ambient.type = .ambient
        ambient.intensity=350
        let ambientNode=SCNNode()
        ambientNode.light=ambient
        scene.rootNode.addChildNode(ambientNode)
    }
    func button(_ text:String,_ selector:Selector)->UIButton {
        let b=UIButton(type:.system)
        b.setTitle(text,for:.normal)
        b.setTitleColor(.systemYellow,for:.normal)
        b.backgroundColor=UIColor(white:0.06,alpha:0.8)
        b.layer.cornerRadius=8
        b.layer.borderWidth=1
        b.layer.borderColor=UIColor.systemYellow.cgColor
        b.titleLabel?.font=.boldSystemFont(ofSize:13)
        b.addTarget(self,action:selector,for:.touchUpInside)
        view.addSubview(b)
        return b
    }
    var bUp:UIButton!,bDown:UIButton!,bLeft:UIButton!,bRight:UIButton!,bFire:UIButton!,bCar:UIButton!,bVault:UIButton!,bStealth:UIButton!,bUpgrade:UIButton!,bLanguage:UIButton!
    func setupHUD(){
        status.textColor = .systemYellow
        status.numberOfLines=2
        status.font = .monospacedSystemFont(ofSize:13,weight:.bold)
        view.addSubview(status)
        message.textColor = .white
        message.numberOfLines=2
        message.font = .systemFont(ofSize:12,weight:.medium)
        view.addSubview(message)
        bUp=button("▲",#selector(moveUp))
        bDown=button("▼",#selector(moveDown))
        bLeft=button("◀",#selector(moveLeft))
        bRight=button("▶",#selector(moveRight))
        for pair in [(bUp!,#selector(moveUp)),(bDown!,#selector(moveDown)),(bLeft!,#selector(moveLeft)),(bRight!,#selector(moveRight))] {
            pair.0.removeTarget(self,action:pair.1,for:.touchUpInside)
            pair.0.addTarget(self,action:pair.1,for:.touchDown)
            pair.0.addTarget(self,action:#selector(stopMoving),for:[.touchUpInside,.touchUpOutside,.touchCancel])
        }
        bFire=button("FIRE",#selector(fire))
        bCar=button("CAR",#selector(carAction))
        bVault=button("HEIST",#selector(vaultAction))
        bStealth=button("HIDE",#selector(hideAction))
        bUpgrade=button("UPGRADE",#selector(upgrade))
        bLanguage=button("العربية/EN",#selector(changeLang))
        refresh()
    }
    override func viewDidLayoutSubviews(){
        super.viewDidLayoutSubviews()
        let w=view.bounds.width, h=view.bounds.height
        let l=max(12,view.safeAreaInsets.left+8)
        let r=max(12,view.safeAreaInsets.right+8)
        status.frame=CGRect(x:l,y:9,width:w*0.70,height:52)
        message.frame=CGRect(x:l,y:60,width:w*0.66,height:40)
        let base=h-max(12,view.safeAreaInsets.bottom)-99
        bUp.frame=CGRect(x:l+49,y:base-49,width:48,height:48)
        bDown.frame=CGRect(x:l+49,y:base+49,width:48,height:48)
        bLeft.frame=CGRect(x:l,y:base,width:48,height:48)
        bRight.frame=CGRect(x:l+98,y:base,width:48,height:48)
        let right=w-r-90
        bFire.frame=CGRect(x:right,y:h-170,width:90,height:46)
        bCar.frame=CGRect(x:right-93,y:h-115,width:90,height:43)
        bStealth.frame=CGRect(x:right,y:h-115,width:90,height:43)
        bVault.frame=CGRect(x:right-93,y:h-68,width:90,height:43)
        bUpgrade.frame=CGRect(x:right,y:h-68,width:90,height:43)
        bLanguage.frame=CGRect(x:right-11,y:16,width:100,height:37)
    }
    @objc func moveUp(){up = -1}
    @objc func moveDown(){up = 1}
    @objc func moveLeft(){side = -1}
    @objc func moveRight(){side = 1}
    @objc func stopMoving(){up=0;side=0}
    @objc func changeLang(){arabic.toggle();refresh()}
    @objc func hideAction(){stealth.toggle();note=stealth ? "Stealth enabled" : "Stealth off";refresh()}
    func distance(_ a:SCNVector3,_ b:SCNVector3)->Float {
        hypot(a.x-b.x,a.z-b.z)
    }
    func canMove(_ x:Float,_ z:Float)->Bool {
        if abs(x)>69 || abs(z)>69 {return false}
        for building in obstructions {
            if abs(x-building.0)<4.2 && abs(z-building.1)<4.2 {return false}
        }
        return true
    }
    @objc func carAction(){
        if let current=activeCar {
            hero.position=SCNVector3(current.position.x+2.5,0.9,current.position.z)
            hero.isHidden=false
            activeCar=nil;speed=0;note="On foot"
        } else if let nearest=cars.min(by:{distance($0.position,hero.position)<distance($1.position,hero.position)}),distance(nearest.position,hero.position)<5 {
            activeCar=nearest
            hero.isHidden=true
            yaw=nearest.eulerAngles.y
            note="Driving: hold ▲ to accelerate, ◀▶ steer"
        } else {note="Move close to a vintage car"}
        refresh()
    }
    @objc func fire(){
        guard ammo>0 && activeCar == nil else{return}
        ammo-=1
        heat=min(5,heat+0.8)
        if let target=police.filter({!$0.isHidden}).min(by:{distance($0.position,hero.position)<distance($1.position,hero.position)}),distance(target.position,hero.position)<14 {
            target.isHidden=true
            money+=50
            note="Officer down +$50"
        } else {note="No target in range"}
        refresh()
    }
    @objc func vaultAction(){
        let loc=activeCar?.position ?? hero.position
        guard distance(loc,SCNVector3(25,0,-19))<9 else {note="Go northeast to the golden bank vault";refresh();return}
        if missionDone {note="Heist already completed"}
        else if lock<5 {lock+=1;note="Lockpicking \(lock)/5"}
        else if drill<6 {drill+=1;note="Vault drilling \(drill)/6";heat=min(5,heat+0.2)}
        else {
            money+=2500
            heat=5
            missionDone=true
            note="HEIST COMPLETE! Escape the police +$2500"
            persist()
        }
        refresh()
    }
    @objc func upgrade(){
        if money>=200 {money-=200;ammo+=40;note="Upgrade: +40 ammo"}
        else {note="Need $200"}
        persist()
        refresh()
    }
    func persist(){UserDefaults.standard.set(money,forKey:"cash");UserDefaults.standard.set(missionDone,forKey:"done")}
    func refresh(){
        let stars=String(repeating:"★",count:min(5,Int(ceil(heat))))
        if arabic {
            status.text="مدينة المشنقة · 1933   مطاردة: \(stars)\nالذخيرة \(ammo)     المال $\(money)"
            message.text=(missionDone ? "المهمة: اهرب من الشرطة" : "المهمة: افتح خزنة البنك شمال شرق المدينة")+"\n"+note
        } else {
            status.text="GALLOWS CITY · 1933  WANTED \(stars)\nAMMO \(ammo)     CASH $\(money)"
            message.text=(missionDone ? "MISSION: Escape the police":"MISSION: Crack the bank vault northeast")+"\n"+note
        }
    }
    @objc func update(_ d:CADisplayLink){
        let dt=last==0 ? Float(1.0/60) : min(Float(d.timestamp-last),0.05)
        last=d.timestamp
        let p=activeCar?.position ?? hero.position
        if let current=activeCar{
            speed=max(-5,min(22,speed-up*dt*10-speed*dt*0.4))
            yaw+=side*dt*(0.3+abs(speed)*0.05)
            let x=current.position.x+sin(yaw)*speed*dt
            let z=current.position.z-cos(yaw)*speed*dt
            if canMove(x,z){current.position=SCNVector3(x,0,z)} else{speed*=0.5}
            current.eulerAngles.y=yaw
        }else{
            let move=stealth ? Float(2.2):Float(5.5)
            let x=hero.position.x+side*move*dt
            let z=hero.position.z+up*move*dt
            if canMove(x,z){hero.position=SCNVector3(x,0.9,z)}
            if abs(up)+abs(side)>0.1{hero.eulerAngles.y=atan2(-side,-up)}
        }
        let position=activeCar?.position ?? hero.position
        cameraNode.position.x+=(position.x-cameraNode.position.x)*min(1,dt*5)
        cameraNode.position.z+=(position.z+19-cameraNode.position.z)*min(1,dt*5)
        for guardNode in police where !guardNode.isHidden {
            let diffX=position.x-guardNode.position.x
            let diffZ=position.z-guardNode.position.z
            let mag=max(0.01,hypot(diffX,diffZ))
            if heat>0.2 && mag<30 {
                let xx=guardNode.position.x+(diffX/mag)*dt*2.9
                let zz=guardNode.position.z+(diffZ/mag)*dt*2.9
                if canMove(xx,zz){guardNode.position=SCNVector3(xx,0.85,zz)}
            }
        }
        heat=max(0,heat-Double(dt)*0.012)
        if Int(d.timestamp*2) % 4 == 0 && Int((d.timestamp-d.duration)*2) % 4 != 0 {refresh()}
    }
}
