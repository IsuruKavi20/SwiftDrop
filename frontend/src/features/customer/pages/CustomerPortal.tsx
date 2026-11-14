import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { navigate, routes, usePath } from '../../../app/router';
import { logOut, refresh } from '../../auth/api/authApi';
import { getAccessToken, readClaims } from '../../auth/lib/session';
import { CompleteProfileForm } from '../components/CompleteProfileForm';
import { CustomerSupportPage } from './CustomerSupportPage';
import { NotificationsPage } from '../../notification/pages/NotificationsPage';
import { NotificationBell } from '../../notification/components/NotificationBell';
import {
  ApiError, STATUS_LABELS, getProfile, getDashboard, listDeliveries, getDelivery,
  estimateDelivery, createDelivery, confirmDelivery, cancelDelivery, trackMine, trackPublic, trackPublicLink, downloadReceipt,
  updateCustomer, getSavedAddresses, addSavedAddress, updateSavedAddress, deleteSavedAddress,
  type Dashboard, type CustomerProfile, type DeliverySummary, type DeliveryStatus,
  type DeliveryDetails, type BookingInput, type FeeEstimate, type Tracking,
  type PagedDeliveries, type SavedAddress,
} from '../api/customerApi';
import s from '../components/CustomerPortal.module.css';
import logoIcon from '../../../assets/auth/logo-box.svg';
import { Icon, type IconName } from '../../../shared/ui/Icon';
import { DeliveryScene } from '../../../shared/ui/DeliveryScene';

// PENDING and CONFIRMED are internal booking states. The assignment calls a confirmed booking CREATED.
const STATUS_FLOW: DeliveryStatus[] = ['CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const fmtMoney = (amount?: number | null) => amount == null ? '—' : `LKR ${Number(amount).toLocaleString('en-LK', {minimumFractionDigits:2,maximumFractionDigits:2})}`;
const fmtDate = (date?: string | null) => date ? new Date(date).toLocaleString('en-LK', {day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'Asia/Colombo'}) : '—';
const fmtShort = (date?: string | null) => date ? new Date(date).toLocaleDateString('en-LK',{day:'numeric',month:'short',year:'numeric'}) : '—';
const message = (error: unknown) => {
  if (error instanceof ApiError && Object.keys(error.fieldErrors ?? {}).length) {
    return `${error.message}: ${Object.entries(error.fieldErrors).slice(0, 4).map(([field, reason]) => `${field}: ${reason}`).join('; ')}`;
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
};
const deliveryUrl = (id: number) => `/customer/deliveries/${id}`;
const link = (to: string, label: string, className?: string) => <button type="button" className={className ?? s.linkButton} onClick={() => navigate(to)}>{label}</button>;
function Status({value}: {value: DeliveryStatus}) {return <span className={`${s.status} ${s[value.toLowerCase()]}`}><span className={s.statusDot} aria-hidden="true"/>{STATUS_LABELS[value]}</span>}
// Loading: grey shapes where the content will appear (no jump when it arrives); the words are for screen readers
function Spinner() {return <div className={s.skeleton} role="status"><span className={s.srOnly}>Loading…</span><i/><i/><i/></div>}
function Notice({children, error=false}:{children:ReactNode;error?:boolean}) {return <div role={error?'alert':'status'} className={error?s.alert:s.notice}>{children}</div>}

export function CustomerPortal() {
  const path=usePath();
  const [profile,setProfile]=useState<CustomerProfile|null>(null);
  const [state,setState]=useState<'loading'|'ready'|'missing'|'error'>('loading');
  const [error,setError]=useState('');
  const load=useCallback(async()=>{
    try {
      if (!getAccessToken() && !(await refresh())) {navigate(routes.login);return}
      const claims=readClaims(getAccessToken()!);
      if (!(claims.entitlements??[]).includes('parcel:read-own')) {navigate(routes.account);return}
      const next=await getProfile();setProfile(next);setState('ready');
    } catch(e) {
      if(e instanceof ApiError && e.status===404) setState('missing');
      else if(e instanceof ApiError && e.status===401) navigate(routes.login);
      else {setError(message(e));setState('error')}
    }
  },[]);
  // load() only sets state after an await (a data fetch on mount), which is what effects are for
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(()=>{void load()},[load]);
  async function logout(){await logOut();navigate(routes.login)}
  const nav=[['Overview',routes.customerDashboard,'overview','Home'],['Send a parcel',routes.customerBook,'send','Send'],['My deliveries',routes.customerDeliveries,'package','Deliveries'],['Track parcel',routes.customerTrack,'pin','Track'],['Notifications',routes.customerNotifications,'bell','Alerts'],['My account',routes.customerAccount,'user','Account'],['Help & support',routes.customerSupport,'help','Help']] as const satisfies readonly (readonly [string,string,IconName,string])[];
  // Two groups in the sidebar: the shipping work first, then account things
  const navGroups=[['Shipping',nav.slice(0,4)],['Account',nav.slice(4)]] as const;
  const displayName=profile?.name||'Customer';
  const selected = path.startsWith('/customer/deliveries/')&&path!==routes.customerBook ? routes.customerDeliveries : path;
  const ownHeading=([routes.customerDashboard,routes.customerBook,routes.customerDeliveries,routes.customerSupport,routes.customerTrack,routes.customerNotifications,routes.customerAccount] as string[]).includes(path)||/^\/customer\/deliveries\/\d+$/.test(path);
  const title=path===routes.customerDashboard?'Overview':path===routes.customerBook?'Create delivery':path===routes.customerDeliveries?'My deliveries':path===routes.customerTrack?'Track a parcel':path===routes.customerNotifications?'Notifications & Alerts':path===routes.customerAccount?'My account':path===routes.customerSupport?'Help & support':'Delivery details';
  return <div className={s.app}>
    <aside className={s.sidebar}>
      <button type="button" className={s.brand} onClick={()=>navigate(routes.customerDashboard)}>
        <span className={s.brandMark}><img src={logoIcon} width={20} height={20} alt="" /></span>
        <span><strong>SwiftDrop</strong><small>Customer portal</small></span>
      </button>
      <nav aria-label="Customer navigation" className={s.nav}>
        {navGroups.map(([group,items])=><div key={group} className={s.navGroup}>
          <span className={s.navLabel}>{group}</span>
          {items.map(([label,to,icon])=><button key={label} type="button" aria-current={selected===to?'page':undefined} className={`${s.navItem} ${selected===to?s.active:''}`} onClick={()=>navigate(to)}><Icon name={icon} className={s.navIcon}/>{label}</button>)}
        </div>)}
      </nav>
      <div className={s.sidebarBottom}>
        <div className={s.accountCard}>
          {profile?.pictureUrl
            ? <img className={s.accountPhoto} src={profile.pictureUrl} alt="" referrerPolicy="no-referrer" width={36} height={36}/>
            : <span className={s.accountInitial}>{displayName.charAt(0).toUpperCase()}</span>}
          <span className={s.accountSummary}><strong>{displayName}</strong><small>Customer account</small></span>
          <button type="button" className={s.signOut} onClick={()=>void logout()} title="Sign out"><Icon name="logout" size={18} label="Sign out"/></button>
        </div>
      </div>
    </aside>
    <div className={s.column}>
      <header className={s.topbar}>
        {/* Pages with their own big heading show only the breadcrumb here, so a title never appears twice */}
        <div><span className={s.eyebrow}>CUSTOMER / {title.toUpperCase()}</span>{ownHeading?<h1 className={s.srOnly}>{title}</h1>:<h1>{title}</h1>}</div>
        <div className={s.topActions}>
          <NotificationBell onViewAll={()=>navigate(routes.customerNotifications)} />
          {link(routes.customerBook,'+ New delivery',s.primarySmall)}
          {/* No avatar here: the account card in the sidebar already shows who is logged in */}
        </div>
      </header>
      <main className={s.content}>
        {state==='loading'&&<Spinner/>}
        {state==='error'&&<><Notice error>{error}</Notice><button onClick={()=>{setState('loading');void load()}}>Retry</button></>}
        {state==='missing'&&<CompleteProfileForm onRegistered={(p)=>{setProfile(p);setState('ready')}}/>}
        {state==='ready'&&<>
          {path===routes.customerDashboard?<DashboardPage name={profile?.name}/>:null}
          {path===routes.customerBook?<BookingPage/>:null}
          {path===routes.customerDeliveries?<DeliveriesPage/>:null}
          {path===routes.customerTrack?<PrivateTrackingSearch/>:null}
          {path===routes.customerNotifications?<NotificationsPage/>:null}
          {path===routes.customerAccount?<ProfilePage profile={profile!} onChange={setProfile}/>:null}
          {path===routes.customerSupport?<CustomerSupportPage/>:null}
          {/^\/customer\/deliveries\/\d+$/.test(path)?<DetailPage id={Number(path.split('/').pop())}/>:null}
        </>}
      </main>
    </div>
    <nav aria-label="Mobile customer navigation" className={s.mobileNav}>{nav.slice(0,6).map(([,to,icon,short])=><button type="button" key={to} aria-current={selected===to?'page':undefined} className={selected===to?s.mobileActive:''} onClick={()=>navigate(to)}><Icon name={icon} size={22}/><small>{short}</small></button>)}</nav>
  </div>;
}

// Greeting by Sri Lanka time
function greeting(){const hour=Number(new Date().toLocaleString('en-LK',{hour:'numeric',hour12:false,timeZone:'Asia/Colombo'}));return hour<12?'Good morning':hour<17?'Good afternoon':'Good evening'}

// One bar split by status (display only); empty grey bar when there are no deliveries yet
function StatusBar({counts}:{counts:Record<DeliveryStatus,number>}){
  const parts=[['Booked',counts.PENDING+counts.CONFIRMED,s.barBooked],['On the way',counts.PICKED_UP+counts.IN_TRANSIT+counts.OUT_FOR_DELIVERY,s.barMoving],['Delivered',counts.DELIVERED,s.barDone],['Failed',counts.FAILED,s.barFailed]] as const;
  const total=parts.reduce((sum,[,n])=>sum+n,0);
  return <div className={s.statusBarWrap}>
    <div className={s.statusBar} role="img" aria-label={parts.map(([label,n])=>`${label} ${n}`).join(', ')}>{total>0&&parts.filter(([,n])=>n>0).map(([label,n,cls])=><span key={label} className={cls} style={{flexGrow:n}}/>)}</div>
    <ul className={s.statusLegend}>{parts.map(([label,n,cls])=><li key={label}><span className={cls}/>{label}<strong>{n}</strong></li>)}</ul>
  </div>;
}

function DashboardPage({name}:{name?:string|null}){
  const [data,setData]=useState<Dashboard|null>(null);const [error,setError]=useState('');
  useEffect(()=>{getDashboard().then(setData).catch(e=>setError(message(e)))},[]);
  if(error)return <Notice error>{error}</Notice>;
  if(!data)return <Spinner/>;
  const stats=[['Total deliveries',data.totalDeliveries,'package'],['Active deliveries',data.activeDeliveries,'truck'],['Delivered',data.countsByStatus.DELIVERED,'check'],['Awaiting confirmation',data.countsByStatus.PENDING,'bell']] as const;
  const actions=[[routes.customerBook,'Book a parcel','Pickup to door, price first','send'],[routes.customerDeliveries,'Delivery history','Every booking and receipt','package'],[routes.customerTrack,'Track a parcel','Live status by number','pin'],[routes.customerNotifications,'Notifications','SMS and email alerts','bell'],[routes.customerSupport,'Get support','Ask about a delivery','help']] as const;
  return <div className={s.stack}>
    <section className={s.hero}>
      <div className={s.heroText}>
        <span className={s.heroEyebrow}>{new Date().toLocaleDateString('en-LK',{weekday:'long',day:'numeric',month:'long',timeZone:'Asia/Colombo'})}</span>
        <h2>{greeting()}{name?`, ${name.split(' ')[0]}`:''}.</h2>
        <p>{data.activeDeliveries>0?`${data.activeDeliveries} ${data.activeDeliveries===1?'parcel is':'parcels are'} on the way right now.`:'No parcels on the way. Ready to send one?'}</p>
        <div className={s.heroActions}>
          <button type="button" className={s.heroPrimary} onClick={()=>navigate(routes.customerBook)}><Icon name="send" size={18}/>Send a parcel</button>
          <button type="button" className={s.heroGhost} onClick={()=>navigate(routes.customerTrack)}><Icon name="search" size={18}/>Track a parcel</button>
        </div>
      </div>
      <DeliveryScene className={s.heroScene}/>
    </section>
    <div className={s.stats}>{stats.map(([name,count,icon])=><div key={name} className={s.stat}><span className={s.statIcon}><Icon name={icon} size={18}/></span><div className={s.muted}>{name}</div><strong>{count}</strong></div>)}</div>
    <div className={s.twoColumns}><section className={s.card}><div className={s.cardHead}><h3>Quick actions</h3></div><div className={s.actionList}>
      {actions.map(([to,title,hint,icon])=><button key={to} type="button" className={s.action} onClick={()=>navigate(to)}><span className={s.actionIcon}><Icon name={icon} size={18}/></span><span className={s.actionText}><strong>{title}</strong><small>{hint}</small></span><Icon name="arrowRight" size={16} className={s.actionArrow}/></button>)}
    </div></section><section className={s.card}><h3>Delivery status</h3><p className={s.muted}>Track the status of your shipments. Courier updates appear automatically on the tracking screen.</p><StatusBar counts={data.countsByStatus}/><div className={s.statusColumns}><div><strong>{data.countsByStatus.IN_TRANSIT+data.countsByStatus.OUT_FOR_DELIVERY}</strong><span>In transit</span></div><div><strong>{data.countsByStatus.FAILED}</strong><span>Failed</span></div></div></section></div>
    <section className={s.card}><div className={s.cardHead}><h3>Recent deliveries</h3>{link(routes.customerDeliveries,'View all →')}</div><DeliveryTable rows={data.recentDeliveries}/></section>
  </div>
}
function DeliveryTable({rows}:{rows:DeliverySummary[]}){
  if(!rows.length)return <div className={s.empty}><span className={s.emptyIcon}><Icon name="package" size={26}/></span><strong>No deliveries here yet</strong><span>Create your first delivery and it will show up here.</span>{link(routes.customerBook,'Send a parcel',s.primary)}</div>;
  return <div className={s.tableScroll}><table className={s.table}><thead><tr><th>Tracking number</th><th>Recipient</th><th>Destination</th><th>Created</th><th>Status</th><th></th></tr></thead><tbody>
    {rows.map(d=><tr key={d.id}><td><span className={s.code}>{d.trackingNumber}</span></td><td>{d.receiverName||'—'}</td><td>{d.destinationCity||'—'}</td><td>{fmtShort(d.createdAt)}</td><td><Status value={d.status}/></td><td className={s.rowAction}>{link(deliveryUrl(d.id),'View',s.viewButton)}</td></tr>)}
  </tbody></table></div>
}

const freshAddress=()=>({contactName:'',contactPhone:'',street:'',city:'',district:'',postalCode:''});
const newBooking=():BookingInput=>({deliveryType:'STANDARD',pickup:freshAddress(),destination:freshAddress(),parcels:[{description:'',weightKg:1,lengthCm:30,widthCm:20,heightCm:10}],specialInstructions:''});
type AddressPart='pickup'|'destination';
function BookingPage(){
  const [draft,setDraft]=useState<BookingInput>(newBooking);
  const [quote,setQuote]=useState<FeeEstimate|null>(null);
  const [confirmed,setConfirmed]=useState<DeliveryDetails|null>(null);
  const [pending,setPending]=useState<DeliveryDetails|null>(null);
  const [savedAddresses,setSavedAddresses]=useState<SavedAddress[]>([]);
  useEffect(()=>{getSavedAddresses().then(setSavedAddresses).catch(()=>undefined)},[]);
  const [busy,setBusy]=useState(false);const [error,setError]=useState('');
  const updateAddress=(which:AddressPart,key:keyof BookingInput['pickup'],value:string)=>setDraft(old=>({...old,[which]:{...old[which],[key]:value}}));
  const sizePresets: Record<string,[number,number,number]> = {SMALL:[20,15,10],MEDIUM:[30,20,10],LARGE:[50,40,30]};
  const sizeOf = (parcel: BookingInput['parcels'][number]) =>
    Object.entries(sizePresets).find(([,d]) => d[0]===parcel.lengthCm && d[1]===parcel.widthCm && d[2]===parcel.heightCm)?.[0] ?? 'CUSTOM';
  const updateParcel=(index:number,key:keyof BookingInput['parcels'][number],value:string)=>
    setDraft(old=>({...old,parcels:old.parcels.map((parcel,i)=>i===index
      ? {...parcel,[key]:key==='description'?value:value===''?undefined:Number(value)} : parcel)}));
  const changeSize=(index:number,size:string)=>{
    if(!sizePresets[size]) return;
    const [lengthCm,widthCm,heightCm]=sizePresets[size];
    setDraft(old=>({...old,parcels:old.parcels.map((parcel,i)=>i===index?{...parcel,lengthCm,widthCm,heightCm}:parcel)}));
  };
  const addParcel=()=>setDraft(old=>old.parcels.length>=20?old:{...old,parcels:[...old.parcels,
    {description:'',weightKg:1,lengthCm:30,widthCm:20,heightCm:10}]});
  const removeParcel=(index:number)=>setDraft(old=>old.parcels.length<=1?old:{...old,parcels:old.parcels.filter((_,i)=>i!==index)});
  async function estimate(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{setQuote(await estimateDelivery(draft))}catch(err){setError(message(err))}finally{setBusy(false)}}
  async function book(){setBusy(true);setError('');try{const created=pending??await createDelivery(draft);setPending(created);setConfirmed(await confirmDelivery(created.id));setQuote(null)}catch(err){setError(message(err))}finally{setBusy(false)}}
  if(confirmed)return <div className={s.stack}>
    <div className={s.headingRow}><div><h2>Booking complete</h2><p>We have your delivery request. A courier will be assigned next.</p></div><BookingSteps current={3}/></div>
    <section className={`${s.card} ${s.successPanel}`}>
      <div className={s.successIcon}><Icon name="check" size={30}/></div>
      <h2>Delivery request confirmed</h2>
      <p>Your booking has been registered in SwiftDrop.</p>
      <div className={s.trackBox}><span>TRACKING NUMBER</span><strong>{confirmed.trackingNumber}</strong><CopyButton text={confirmed.trackingNumber}/><Status value={confirmed.status}/></div>
      <dl className={s.successFacts}>
        <div><dt>Estimated charge</dt><dd>{fmtMoney(confirmed.estimatedFee)}</dd></div>
        <div><dt>Estimated delivery</dt><dd>{fmtShort(confirmed.estimatedDeliveryDate)}</dd></div>
      </dl>
      <div className={s.centerButtons}>{link(deliveryUrl(confirmed.id),'Track delivery',s.primary)}{link(routes.customerDeliveries,'My deliveries',s.secondary)}</div>
    </section>
  </div>;
  return <div className={s.stack}><div className={s.headingRow}><div><h2>{quote?'Review & confirm':'New parcel booking'}</h2><p>{quote?'Check the estimated fee and shipment details before booking.':'Fill in pickup, destination and parcel information.'}</p></div><BookingSteps current={quote?1:0}/></div>
    {error&&<Notice error>{error}</Notice>}
    {quote?<div className={s.reviewGrid}>
      <section className={s.card}><h3>Shipment summary</h3>
        <div className={s.reviewRoute}>
          <div className={s.reviewStop}><span className={s.reviewDot}/><div><small>Pickup</small><strong>{draft.pickup.contactName}</strong><span>{draft.pickup.street}, {draft.pickup.city}, {draft.pickup.district}</span><span>{draft.pickup.contactPhone}</span></div></div>
          <div className={s.reviewStop}><span className={`${s.reviewDot} ${s.reviewDotEnd}`}><Icon name="pin" size={12}/></span><div><small>Deliver to</small><strong>{draft.destination.contactName}</strong><span>{draft.destination.street}, {draft.destination.city}, {draft.destination.district}</span><span>{draft.destination.contactPhone}</span></div></div>
        </div>
        <div className={s.reviewChips}>
          <span><Icon name={draft.deliveryType==='STANDARD'?'truck':draft.deliveryType==='EXPRESS'?'zap':'send'} size={15}/>{draft.deliveryType.replace('_',' ').toLowerCase()}</span>
          <span><Icon name="package" size={15}/>{draft.parcels.length} parcel{draft.parcels.length===1?'':'s'}</span>
          <span>{draft.parcels.reduce((sum,p)=>sum+(Number(p.weightKg)||0),0).toFixed(2)} kg total</span>
        </div>
      </section>
      <section className={`${s.card} ${s.receipt}`}><h3>Estimated delivery fee</h3>
        <div className={s.billRow}><span>Base fee</span><strong>{fmtMoney(quote.baseFee)}</strong></div>
        <div className={s.billRow}><span>Weight charge</span><strong>{fmtMoney(quote.weightCharge)}</strong></div>
        <div className={s.billRow}><span>Service surcharge</span><strong>{fmtMoney(quote.serviceCharge)}</strong></div>
        <div className={s.total}><span>Total estimate</span><strong>{fmtMoney(quote.totalFee)}</strong></div>
        <p className={s.smallNote}>{quote.explanation}</p>
        {pending&&<Notice>Your booking was saved. Retry confirmation to finish it safely without creating a duplicate.</Notice>}
        <div className={s.receiptActions}>
          <button type="button" className={s.primary} onClick={()=>void book()} disabled={busy}>{busy?'Processing…':pending?'Retry confirmation':'Confirm delivery'}</button>
          <button type="button" className={s.secondary} disabled={pending!==null} title={pending?"Booking saved; retry confirmation rather than creating another booking.":undefined} onClick={()=>setQuote(null)}>Edit details</button>
        </div>
      </section>
    </div>:
    <form onSubmit={e=>void estimate(e)} className={s.stack}>
      {(['pickup','destination'] as AddressPart[]).map(which=><section className={s.card} key={which}><div className={s.cardHead}><h3 className={s.sectionTitle}><span className={s.sectionNum}>{which==='pickup'?1:2}</span><Icon name={which==='pickup'?'package':'pin'} size={18} className={s.sectionIcon}/>{which==='pickup'?'Pickup details':'Recipient & delivery destination'}</h3></div>{savedAddresses.length>0&&<label className={s.field}><span>Fill address from saved locations (optional)</span><select defaultValue="" onChange={e=>{const chosen=savedAddresses.find(a=>String(a.id)===e.target.value);if(chosen)setDraft(prev=>({...prev,[which]:{...prev[which],street:chosen.street,city:chosen.city,district:chosen.district,postalCode:chosen.postalCode??''}}))}}><option value="">Enter a new address</option>{savedAddresses.map(a=><option value={a.id} key={a.id}>{a.label} — {a.city}</option>)}</select></label>}<div className={s.formGrid}>{([['contactName',which==='pickup'?'Sender full name':'Recipient full name'],['contactPhone','Contact number'],['street','Street address'],['city','City'],['district','District'],['postalCode','Postal code (optional)']] as [keyof BookingInput['pickup'],string][]).map(([key,label])=><label className={key==='street'?s.wide:s.field} key={key}><span>{label}</span><input required={key!=='postalCode'} value={draft[which][key]} onChange={e=>updateAddress(which,key,e.target.value)} placeholder={label} maxLength={key==='street'?255:100} type={key==='contactPhone'?'tel':'text'} pattern={key==='contactPhone'?"^\\+?[0-9]{9,15}$":undefined} /></label>)}</div></section>)}
      <section className={s.card}><div className={s.cardHead}><h3 className={s.sectionTitle}><span className={s.sectionNum}>3</span><Icon name="truck" size={18} className={s.sectionIcon}/>Parcel details</h3>
        <button type="button" className={s.secondary} disabled={draft.parcels.length>=20} onClick={addParcel}>+ Add parcel</button></div>
        <fieldset className={s.serviceGroup}><legend>Delivery service</legend>
          {([['STANDARD','Standard','About 3 days · no extra fee','truck'],['EXPRESS','Express','Next day · small extra fee','zap'],['SAME_DAY','Same day','Today · higher extra fee','send']] as const).map(([value,title,hint,icon])=>
            <label key={value} className={`${s.serviceCard} ${draft.deliveryType===value?s.serviceOn:''}`}>
              <input type="radio" name="deliveryType" value={value} checked={draft.deliveryType===value} onChange={()=>setDraft(p=>({...p,deliveryType:value}))}/>
              <span className={s.serviceIcon}><Icon name={icon} size={18}/></span><span><strong>{title}</strong><small>{hint}</small></span>
            </label>)}
        </fieldset>
        {draft.parcels.map((parcel,index)=><div className={s.parcelCard} key={index}>
          <div className={s.cardHead}><h4>Parcel {index+1} of {draft.parcels.length}</h4>
            {draft.parcels.length>1&&<button type="button" className={s.destructive} onClick={()=>removeParcel(index)}>Remove parcel</button>}</div>
          <div className={s.formGrid}>
            <label className={s.field}><span>Parcel size</span>
              <select value={sizeOf(parcel)} onChange={e=>changeSize(index,e.target.value)}>
                <option value="SMALL">Small</option><option value="MEDIUM">Medium</option>
                <option value="LARGE">Large</option><option value="CUSTOM">Custom dimensions</option>
              </select></label>
            {([['description','Parcel contents'],['weightKg','Weight (kg)'],['lengthCm','Length (cm)'],['widthCm','Width (cm)'],['heightCm','Height (cm)'],['declaredValue','Declared value (LKR, optional)']] as [keyof BookingInput['parcels'][number],string][]).map(([key,label])=><label className={s.field} key={key}>
              <span>{label}</span><input required={key!=='declaredValue'} type={key==='description'?'text':'number'}
                min={key==='description'?undefined:key==='declaredValue'?'0':'0.01'}
                step={key==='description'?undefined:'0.01'}
                value={parcel[key]??''} onChange={e=>updateParcel(index,key,e.target.value)} />
              </label>)}
          </div>
        </div>)}
        <label className={s.wide}><span>Special instructions (optional)</span>
          <textarea rows={2} maxLength={500} value={draft.specialInstructions??''}
            onChange={e=>setDraft(p=>({...p,specialInstructions:e.target.value}))}
            placeholder="Gate number, pickup instructions…" /></label>
        <p className={s.smallNote}>You can send up to 20 parcels in one delivery request.</p>
      </section><div className={s.formBar}><span className={s.formBarNote}><Icon name="shield" size={16}/>You see the price before anything is booked.</span><button type="submit" className={s.primary} disabled={busy}>{busy?'Calculating…':'Get delivery estimate →'}</button></div>
    </form>}
  </div>
}
// Booking progress: 0 = details, 1 = review, 3 = all done (display only)
function BookingSteps({current}:{current:number}){
  return <ol className={s.stepper} aria-label="Booking steps">{['Details','Review','Booked'].map((label,i)=>{
    const done=i<current;const now=i===current;
    return <li key={label} className={done?s.stepDone:now?s.stepNow:undefined} aria-current={now?'step':undefined}><span>{done?<Icon name="check" size={14}/>:i+1}</span>{label}</li>})}</ol>;
}

// Copies the tracking number; shows "Copied" for a moment
function CopyButton({text}:{text:string}){
  const [copied,setCopied]=useState(false);
  async function copy(){try{await navigator.clipboard.writeText(text);setCopied(true);window.setTimeout(()=>setCopied(false),1600)}catch{/* clipboard blocked: the number is still on screen */}}
  return <button type="button" className={s.copyButton} onClick={()=>void copy()} aria-live="polite">{copied?'Copied':'Copy'}</button>;
}


function DeliveriesPage(){
  const [data,setData]=useState<PagedDeliveries|null>(null),[status,setStatus]=useState(''),[search,setSearch]=useState(''),[page,setPage]=useState(0),[error,setError]=useState('');
  // Loading = the last finished request was for other filters (derived, so the effect never sets state synchronously)
  const requestKey=`${page}|${status}|${search}`;const [doneKey,setDoneKey]=useState<string|null>(null);const loading=doneKey!==requestKey;
  useEffect(()=>{let active=true;listDeliveries({page,status,search}).then(p=>{if(active)setData(p)}).catch(e=>{if(active)setError(message(e))}).finally(()=>{if(active)setDoneKey(`${page}|${status}|${search}`)});return()=>{active=false}},[page,status,search]);
  return <div className={s.stack}><div className={s.headingRow}><div><h2>All your deliveries</h2><p>Search, review and track every booking in one place.</p></div></div><section className={s.card}><div className={s.filterRow}><label className={s.field}><span>Search deliveries</span><span className={s.inputIcon}><Icon name="search" size={16}/><input value={search} onChange={e=>{setSearch(e.target.value);setPage(0)}} placeholder="Tracking, recipient or city" /></span></label><label className={s.field}><span>Status</span><select value={status} onChange={e=>{setStatus(e.target.value);setPage(0)}}><option value="">All statuses</option>{Object.entries(STATUS_LABELS).map(([k,v])=><option value={k} key={k}>{v}</option>)}</select></label></div>{error&&<Notice error>{error}</Notice>}{loading?<Spinner/>:<DeliveryTable rows={data?.content??[]}/>}<div className={s.pagination}><span>{data?.totalElements??0} deliveries</span><div><button className={s.secondary} disabled={page===0} onClick={()=>setPage(n=>n-1)}>Previous</button><span> Page {page+1} of {Math.max(1,data?.totalPages??1)} </span><button className={s.secondary} disabled={page+1>=(data?.totalPages??1)} onClick={()=>setPage(n=>n+1)}>Next</button></div></div></section></div>
}

function TrackingTimeline({status,events}:{status:DeliveryStatus;events:Tracking['events']}){
  if (status === 'PENDING') {
    return <div className={s.timeline}><div className={s.timelineItem}><span className={`${s.timelineDot} ${s.timelineNow}`}><Icon name="bell" size={14}/></span><div><strong>Awaiting confirmation</strong><p>Confirm your booking to submit the delivery request.</p></div></div></div>;
  }
  const terminal = status === 'FAILED' || status === 'CANCELLED';
  const progressEvents = events.filter(e => STATUS_FLOW.includes(e.status));
  const lastProgress = progressEvents.at(-1)?.status ?? 'CONFIRMED';
  const current = Math.max(0, STATUS_FLOW.indexOf(terminal ? lastProgress : status));
  return <div className={s.timeline}>{STATUS_FLOW.map((stage,i)=>{
    if (terminal && stage === 'DELIVERED') return null;
    const event = events.find(e=>e.status===stage);
    // reached steps get a check; the next step (what the parcel is waiting for) gets the pulsing ring
    const done=i<=current;const now=!terminal&&i===current+1;
    return <div className={`${s.timelineItem} ${i<current?s.timelineLineDone:''}`} key={stage} aria-current={now?'step':undefined}><span className={`${s.timelineDot} ${done?s.timelineDone:''} ${now?s.timelineNow:''}`}>{done?<Icon name="check" size={14}/>:now?<Icon name="truck" size={14}/>:<span className={s.timelineEmpty}/>}</span><div><strong>{STATUS_LABELS[stage]}</strong><p>{event ? (stage==='CONFIRMED'?'Delivery request created':event.description) : i>current?'Awaiting update':''}</p><small>{event?fmtDate(event.eventTime):''}</small></div></div>;
  })}{terminal&&<div className={s.timelineItem}><span className={`${s.timelineDot} ${s.timelineFailed}`} aria-hidden="true">!</span><div><strong>{STATUS_LABELS[status]}</strong><p>{events.find(e=>e.status===status)?.description||'Delivery was not completed.'}</p><small>{fmtDate(events.find(e=>e.status===status)?.eventTime)}</small></div></div>}</div>;
}
function DetailPage({id}:{id:number}){
  const [data,setData]=useState<DeliveryDetails|null>(null);const [error,setError]=useState('');const [downloading,setDownloading]=useState(false);const [cancelling,setCancelling]=useState(false);const [reason,setReason]=useState('');const [updating,setUpdating]=useState(false);const [success,setSuccess]=useState('');
  useEffect(()=>{let live=true;const fetchOne=()=>getDelivery(id).then(d=>{if(live)setData(d)}).catch(e=>{if(live)setError(message(e))});void fetchOne();const timer=window.setInterval(()=>void fetchOne(),15000);return()=>{live=false;window.clearInterval(timer)}},[id]);
  async function receipt(){setDownloading(true);setError('');try{await downloadReceipt(id)}catch(e){setError(message(e))}finally{setDownloading(false)}}
  async function cancel(e:FormEvent){e.preventDefault();setUpdating(true);setError('');setSuccess('');try{
    setData(await cancelDelivery(id,reason));setCancelling(false);setSuccess('Delivery cancelled.');
  }catch(e){setError(message(e))}finally{setUpdating(false)}}
  async function confirmPending(){setUpdating(true);setError('');setSuccess('');try{
    setData(await confirmDelivery(id));setSuccess('Delivery confirmed.');
  }catch(e){setError(message(e))}finally{setUpdating(false)}}
  if(error&&!data)return <Notice error>{error}</Notice>;
  if(!data)return <Spinner/>;
  return <div className={s.stack}><div className={s.headingRow}><div><button type="button" className={s.backLink} onClick={()=>navigate(routes.customerDeliveries)}><Icon name="arrowRight" size={14} className={s.backIcon}/>All deliveries</button><h2 className={s.detailCode}>{data.trackingNumber}<CopyButton text={data.trackingNumber}/></h2><p>Booked {fmtDate(data.createdAt)} · Automatically checks for updates every 15 seconds</p></div><Status value={data.status}/></div>{data.status==='CANCELLED'&&data.cancellationReason&&<Notice>Reason: {data.cancellationReason}</Notice>}{error&&<Notice error>{error}</Notice>}{success&&<Notice>{success}</Notice>}
  <div className={s.twoColumns}><section className={s.card}><h3>Delivery progress</h3><TrackingTimeline status={data.status} events={data.trackingEvents}/></section><div className={s.stack}><section className={s.card}><h3>Delivery information</h3><div className={s.reviewRoute}>
          <div className={s.reviewStop}><span className={s.reviewDot}/><div><small>Pickup</small><strong>{data.pickup.contactName}</strong><span>{data.pickup.street}, {data.pickup.city}, {data.pickup.district}</span></div></div>
          <div className={s.reviewStop}><span className={`${s.reviewDot} ${s.reviewDotEnd}`}><Icon name="pin" size={12}/></span><div><small>Recipient</small><strong>{data.destination.contactName}</strong><span>{data.destination.street}, {data.destination.city}, {data.destination.district}</span><span>{data.destination.contactPhone}</span></div></div>
        </div><div className={s.summaryDivider}/><h3>Parcels ({data.parcels.length})</h3>{data.parcels.map((parcel,i)=><div className={s.billRow} key={parcel.id??i}><span>{parcel.description}</span><strong>{parcel.weightKg} kg · {parcel.lengthCm} × {parcel.widthCm} × {parcel.heightCm} cm</strong></div>)}<div className={s.billRow}><span>Estimated fee</span><strong>{fmtMoney(data.estimatedFee)}</strong></div><div className={s.billRow}><span>Estimated delivery</span><strong>{fmtShort(data.estimatedDeliveryDate)}</strong></div></section>{(data.status==='PENDING'||data.status==='CONFIRMED')&&<section className={s.card}>
      <h3>Manage this booking</h3>
      {data.status==='PENDING'&&<><p className={s.muted}>This saved booking is waiting for confirmation.</p>
        <button type="button" className={s.primary} disabled={updating} onClick={()=>void confirmPending()}>Confirm delivery</button></>}
      {!cancelling?<div className={s.actions}><button type="button" className={s.destructive}
        onClick={()=>setCancelling(true)}>Cancel this booking</button></div>:
        <form onSubmit={e=>void cancel(e)} className={s.stack}>
          <p className={s.muted}>Cancellation is allowed only before courier pickup or assignment.</p>
          <label className={s.wide}><span>Reason (optional)</span><textarea rows={2} maxLength={200}
            value={reason} onChange={e=>setReason(e.target.value)} placeholder="Why are you cancelling?" /></label>
          <div className={s.actions}><button type="button" className={s.secondary} onClick={()=>setCancelling(false)}>Keep booking</button>
            <button type="submit" className={s.destructive} disabled={updating}>{updating?'Cancelling…':'Confirm cancellation'}</button></div>
        </form>}
    </section>}
    <section className={s.card}><h3>Receipt & sharing</h3><p className={s.muted}>Your receipt is available after successful delivery.</p><button className={s.primary} disabled={data.status!=='DELIVERED'||downloading} onClick={()=>void receipt()}>{downloading?'Preparing…':'Download PDF receipt'}</button><p className={s.muted}>Public tracking number: <strong className={s.mono}>{data.trackingNumber}</strong></p><button className={s.secondary} onClick={()=>void navigator.clipboard.writeText(`${window.location.origin}/track/${encodeURIComponent(data.trackingNumber)}`)}>Copy public tracking link</button><div className={s.actions}><button className={s.subtleLink} onClick={()=>navigate(`${routes.customerSupport}?delivery=${id}`)}>Need help with this delivery?</button></div></section></div></div></div>;
}

function PrivateTrackingSearch(){
  const [number,setNumber]=useState('');
  const [selected,setSelected]=useState('');
  const [data,setData]=useState<Tracking|null>(null);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  useEffect(()=>{
    if(!selected)return;
    let active=true;
    const update=()=>trackMine(selected).then(result=>{if(active){setData(result);setError('')}}).catch(e=>{if(active)setError(message(e))});
    void update();
    const timer=window.setInterval(()=>void update(),15000);
    return()=>{active=false;window.clearInterval(timer)};
  },[selected]);
  async function submit(e:FormEvent){
    e.preventDefault();setBusy(true);setError('');setData(null);
    try{const code=number.trim();const result=await trackMine(code);setSelected(code);setData(result)}
    catch(e){setSelected('');setError(message(e))}
    finally{setBusy(false)}
  }
  return <div className={s.stack}><div className={s.headingRow}><div><h2>Track a parcel</h2><p>Enter a tracking number from your account to see where it is right now.</p></div></div>
  <section className={s.publicSearch}><form onSubmit={e=>void submit(e)} className={s.searchBar}><span className={s.searchIcon}><Icon name="search"/></span><input aria-label="Your tracking number" required maxLength={30} placeholder="SD-XXXXXXXXXX" value={number} onChange={e=>setNumber(e.target.value)}/><button className={s.primary} disabled={busy}>{busy?'Searching…':'Track my parcel'}</button></form>{error&&<Notice error>{error}</Notice>}</section>
  {!data&&!error&&<div className={s.trackHint}><span className={s.emptyIcon}><Icon name="pin" size={24}/></span><strong>Your parcel's journey shows here</strong><span>Tip: you can also open any delivery from {link(routes.customerDeliveries,'My deliveries',s.inlineLink)} to see its full details.</span></div>}
  {data&&<section className={s.card}><div className={s.cardHead}><div><span className={s.eyebrow}>YOUR DELIVERY</span><h2 className={s.mono}>{data.trackingNumber}</h2></div><Status value={data.status}/></div><div className={s.route}><div><small>FROM</small><strong>{data.fromCity||'—'}</strong></div><span className={s.routeLine} aria-hidden="true"><Icon name="truck" size={16}/></span><div><small>TO</small><strong>{data.toCity||'—'}</strong></div></div><TrackingTimeline status={data.status} events={data.events}/><p className={s.smallNote}>Automatically refreshes every 15 seconds while this page is open.</p></section>}</div>;
}
/** "link:<code>" = a tracking link from a message; anything else = a tracking number. */
const lookupTracking=(key:string)=>key.startsWith('link:')?trackPublicLink(key.slice(5)):trackPublic(key);

export function PublicTrackingPage(){
  const path=usePath();
  // Two kinds of address: /track/link/<random code> (the link from the SMS/email: can't be guessed) and
  // /track/<tracking number> (typed by hand). The link code is never shown in the search box.
  const LINK_PREFIX='/track/link/';
  const isLink=path.startsWith(LINK_PREFIX);
  const initial=isLink?`link:${decodeURIComponent(path.slice(LINK_PREFIX.length))}`:path.startsWith('/track/')?decodeURIComponent(path.split('/').slice(2).join('/')):'';
  const [number,setNumber]=useState(isLink?'':initial);const [data,setData]=useState<Tracking|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(false);
  // A new tracking number in the address resets the page while rendering (React's "adjust state on prop change")
  const [shownFor,setShownFor]=useState(initial);
  if(shownFor!==initial){setShownFor(initial);setNumber(isLink?'':initial);setData(null);setError('')}
  useEffect(()=>{if(!initial)return;let live=true;const run=()=>lookupTracking(initial).then(d=>{if(live){setData(d);setError('')}}).catch(e=>{if(live)setError(message(e))});void run();const timer=window.setInterval(()=>void run(),15000);return()=>{live=false;clearInterval(timer)}},[initial]);
  async function search(e:FormEvent){e.preventDefault();setLoading(true);setError('');try{const result=await trackPublic(number);setData(result);navigate(`/track/${encodeURIComponent(number.trim())}`)}catch(e){setData(null);setError(message(e))}finally{setLoading(false)}}
  return <div className={s.publicPage}><header className={s.publicHeader}><button type="button" className={s.publicBrand} onClick={()=>navigate('/')}><span className={s.brandMark}><img src={logoIcon} width={20} height={20} alt="" /></span><strong>SwiftDrop</strong></button><div>{link(routes.login,'Log in',s.secondary)}</div></header><main className={s.publicBody}><div className={s.publicIntro}><span className={s.publicEyebrow}><span className={s.liveDot} aria-hidden="true"/>Parcel tracking · no account needed</span><h1>Know where your <em>parcel</em> is.</h1><p>Track SwiftDrop deliveries using the tracking number supplied by your sender.</p></div><section className={s.publicSearch}><form className={s.searchBar} onSubmit={e=>void search(e)}><span className={s.searchIcon}><Icon name="search"/></span><input aria-label="Tracking number" required placeholder="SD-XXXXXXXXXX" value={number} onChange={e=>setNumber(e.target.value)}/><button className={s.primary} disabled={loading}>{loading?'Searching…':'Track parcel'}</button></form>{error&&<Notice error>{error}</Notice>}</section>
  {!data&&!error&&<ul className={s.publicTips}>{([['message','Find your number','It is in the SMS or email from your sender, and starts with SD-.'],['zap','Live updates','This page refreshes by itself while you keep it open.'],['shield','Private by design','Only the town and status show. No names, phones or addresses.']] as const).map(([icon,title,text])=><li key={title}><span className={s.tipIcon}><Icon name={icon}/></span><strong>{title}</strong><span>{text}</span></li>)}</ul>}
  {data&&<section className={s.card}><div className={s.cardHead}><div><span className={s.eyebrow}>TRACKING NUMBER</span><h2 className={s.mono}>{data.trackingNumber}</h2></div><Status value={data.status}/></div><div className={s.route}><div><small>FROM</small><strong>{data.fromCity||'—'}</strong></div><span className={s.routeLine} aria-hidden="true"><Icon name="truck" size={16}/></span><div><small>TO</small><strong>{data.toCity||'—'}</strong></div></div><TrackingTimeline status={data.status} events={data.events}/><p className={s.smallNote}>Updates refresh automatically. Personal contact details and addresses are not shown publicly.</p></section>}
  </main></div>
}

function ProfilePage({profile,onChange}:{profile:CustomerProfile;onChange:(p:CustomerProfile)=>void}){
  const [phone,setPhone]=useState(profile.phoneNumber);
  const [fullName,setFullName]=useState(profile.name??'');
  const [addresses,setAddresses]=useState<SavedAddress[]>([]);
  const [error,setError]=useState(''),[success,setSuccess]=useState(''),[busy,setBusy]=useState(false);
  const [editingId,setEditingId]=useState<number|null>(null);
  const emptyAddress=()=>({label:'Home',street:'',city:'',district:'',postalCode:'',defaultAddress:false});
  const [addr,setAddr]=useState(emptyAddress);
  const reload=useCallback(()=>getSavedAddresses().then(setAddresses).catch(e=>setError(message(e))),[]);
  useEffect(()=>{void reload()},[reload]);
  async function save(e:FormEvent){e.preventDefault();setBusy(true);setError('');setSuccess('');try{onChange(await updateCustomer(phone,fullName));setSuccess('Profile updated.')}catch(e){setError(message(e))}finally{setBusy(false)}}
  async function add(e:FormEvent){e.preventDefault();setBusy(true);setError('');setSuccess('');try{
    if(editingId!==null)await updateSavedAddress(editingId,addr);
    else await addSavedAddress(addr);
    setAddr(emptyAddress());setEditingId(null);setSuccess(editingId!==null?'Address updated.':'Address saved.');await reload();
  }catch(e){setError(message(e))}finally{setBusy(false)}}
  async function remove(id:number){setError('');try{await deleteSavedAddress(id);if(editingId===id){setEditingId(null);setAddr(emptyAddress())}await reload();setSuccess('Address removed.')}catch(e){setError(message(e))}}
  function edit(address:SavedAddress){setEditingId(address.id);setAddr({label:address.label,street:address.street,city:address.city,district:address.district,postalCode:address.postalCode??'',defaultAddress:address.defaultAddress});setSuccess('');setError('')}
  return <div className={s.stack}>{error&&<Notice error>{error}</Notice>}{success&&<Notice>{success}</Notice>}
    <div className={s.headingRow}><div><h2>My account</h2><p>Your details and the addresses you send from.</p></div></div>
    <section className={s.profileHero}>
      {profile.pictureUrl
        ? <img className={s.profilePhoto} src={profile.pictureUrl} alt="" referrerPolicy="no-referrer" width={72} height={72}/>
        : <span className={s.profileInitial}>{(profile.name||'C').charAt(0).toUpperCase()}</span>}
      <div className={s.profileWho}><strong>{profile.name||'Customer'}</strong>{profile.email&&<span>{profile.email}</span>}<span className={s.profileBadge}><Icon name="shield" size={13}/>Customer account</span></div>
    </section>
    <section className={s.card}><h3 className={s.sectionTitle}><span className={s.sectionIconBox}><Icon name="user" size={16}/></span>Personal details</h3><p className={s.muted}>Your display name and contact number are saved in SwiftDrop. Your password and sign-in are managed by the SwiftDrop account service.</p><form onSubmit={e=>void save(e)}><div className={s.formGrid}><label className={s.field}><span>Full name</span><input type="text" required maxLength={150} value={fullName} onChange={e=>setFullName(e.target.value)} /></label><label className={s.field}><span>Phone number</span><input type="tel" value={phone} required maxLength={16} pattern="^\+?[0-9]{9,15}$" onChange={e=>setPhone(e.target.value)}/></label></div><div className={s.actions}><button className={s.primary} disabled={busy}>Save profile</button></div></form></section>
    <section className={s.card}><h3 className={s.sectionTitle}><span className={s.sectionIconBox}><Icon name="pin" size={16}/></span>Saved addresses</h3>{addresses.length?<div className={s.addressGrid}>{addresses.map(a=><div className={`${s.addressCard} ${editingId===a.id?s.addressEditing:''}`} key={a.id}><div className={s.addressTop}><strong>{a.label}</strong>{a.defaultAddress&&<span className={s.defaultBadge}>Default</span>}</div><p>{a.street}, {a.city}, {a.district}{a.postalCode?` ${a.postalCode}`:''}</p><div className={s.inlineActions}><button className={s.viewButton} type="button" onClick={()=>edit(a)}>Edit</button><button className={s.removeButton} type="button" onClick={()=>void remove(a.id)}>Remove</button></div></div>)}</div>:<p className={s.muted}>No addresses saved yet. Add one below to fill bookings faster.</p>}
    <h3 className={s.subheading}>{editingId===null?'Add an address':'Edit saved address'}</h3><form className={s.formGrid} onSubmit={e=>void add(e)}>{([['label','Label'],['street','Street'],['city','City'],['district','District'],['postalCode','Postal code']] as const).map(([k,label])=><label className={s.field} key={k}><span>{label}</span><input maxLength={k==='street'?255:100} value={addr[k]} required={k!=='postalCode'} onChange={e=>setAddr(p=>({...p,[k]:e.target.value}))}/></label>)}<label className={s.check}><input type="checkbox" checked={addr.defaultAddress} onChange={e=>setAddr(p=>({...p,defaultAddress:e.target.checked}))}/> Set as default</label><div className={s.alignRight}>{editingId!==null&&<button type="button" className={s.secondary} onClick={()=>{setEditingId(null);setAddr(emptyAddress())}}>Cancel edit</button>}<button disabled={busy} className={s.primary}>{editingId!==null?'Save changes':'Save address'}</button></div></form></section></div>;
}
