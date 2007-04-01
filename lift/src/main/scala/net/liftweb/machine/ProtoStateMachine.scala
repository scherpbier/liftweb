package net.liftweb.machine;

/*                                                *\
 (c) 2006-2007 WorldWide Conferencing, LLC
 Distributed under an Apache License
 http://www.apache.org/licenses/LICENSE-2.0
 \*                                                 */

import net.liftweb.mapper._
import net.liftweb.proto._
import net.liftweb.util.Helpers._
import scala.collection.mutable.{Queue, HashMap}
import scala.actors.Actor
import scala.actors.Actor._
import net.liftweb.util._

/**
 *  This trait manages state/workflow transition 
 */
trait ProtoStateMachine[MyType <: ProtoStateMachine[MyType, StateType], 
			StateType <: Enumeration] extends KeyedMapper[long, MyType] 
{
  /**
    * Shorthand for one of the states
    */
  type StV = StateType#Value
  
  /**
    *  Shorthand for the meta state machine
    */
  type Meta = MetaProtoStateMachine[MyType, StateType]

  /**
    * the primary key for the database
    */
  val id = new MappedLongIndex[MyType](this)
  
  /**
    * get the primary key field
    */
  override def primaryKeyField = id
  
  /**
    * Implement a method that returns the singleton
    */
  def getSingleton: Meta
  
  /**
    * The column in the database that stores the current state
    */
  val currentState = new MappedInt[MyType](this)
  
  /**
    * The column in the database that stores the next time an event should go off
    */
  val timedEventAt = new MappedLong[MyType](this) 
  
  val nextTransitionAt = new MappedLong[MyType](this) with LifecycleCallbacks {
    override def beforeSave {if (this.get < System.currentTimeMillis) this := -1L}
    override def dbIndexed_?  = true
  }
  
  def setupTime(when: TimeSpan) {
    val trigger = timedEventAt.get + when.len
    if (trigger >= System.currentTimeMillis && (nextTransitionAt.get <= 0L || trigger < nextTransitionAt.get)) nextTransitionAt := trigger
  }
  
  /**
    * Get the current state
    */
  def state: StateType#Value = getSingleton.stateEnumeration(currentState.get)
  
  
  /**
    * This method is called on a transition from one state to another.  Override this method
    * to perform an action.  Please call super to actually change the state and save the instance 
    */
  def transition(from: StV, to: StV, why: Meta#Event): unit = {
    this.currentState := to.id
    this.save
  }
  
  /**
    * This item has reached a terminating state.  This method will remove the
    * item from the database.  Override this method (please call super at the end of your method)
    * to do any cleanup.
    */
  def terminate(from: StV,to: StV,event: Meta#Event): unit = {
    this.delete_!
  }
    
  /**
    * Process an event
    */
  def processEvent(event: Meta#Event):unit = {
    synchronized {
      eventQueue += event
    }
    
    def processIt {
      val toProcess = synchronized {
        if (_isProcessing || eventQueue.isEmpty) None
        else {_isProcessing = true; Some(eventQueue.dequeue)}
      }
      
      toProcess.foreach {
        event =>
        try {
          getSingleton.processEvent(this, event)
        } finally {
          synchronized {_isProcessing = false}
          processIt
        }
      }
    }
    
    processIt
  }
  
  private var _isProcessing = false
  private val eventQueue = new Queue[Meta#Event] 
}

/**
  * A singleton that implements this trait will manage transitions, etc. for the state machine instance
  */
trait MetaProtoStateMachine [MyType <: ProtoStateMachine[MyType, StateType], 
                             StateType <: Enumeration] extends KeyedMetaMapper[long, MyType] with ProtoStateMachine[MyType, StateType] {
    

  /**
    * This method must be implemented.  It defines the states and legal state transitions
    */
  protected def states : List[State];
  
  /**
    * Any transitions that are applied to all states can be listed here
    */
  protected def globalTransitions: List[ATransition]
  
  // the state transition table
  private val stateInfo = new HashMap[StV, Seq[ATransition]]
  private val stateList = new HashMap[StV, State]
                                      
  // process the states
  states.foreach {
    st =>
    if (stateList.get(st.name).isDefined) throw new DuplicateStateException("State "+st.name+" is defined twice")
    stateInfo(st.name) = st.trans ++ globalTransitions
    stateList(st.name) = st
  }
                     
                              
  /**
    * The default initial state
    */
  def initialState : StV
  
  /**
    * The enumeration of states
    */
  def stateEnumeration: StateType
  
  /**
    *  Terminate an instance
    */
  def terminate(what: MyType,from: StV,to: StV,event: Meta#Event) {what.terminate(from, to, event)}
  

  protected def instantiate: MyType

  def newInstance(firstEvent: Meta#Event): MyType = createNewInstance(firstEvent, None)  
  def createNewInstance(firstEvent: Meta#Event, setup: Option[(MyType) => Any]): MyType = {
    val state = instantiate
    setup.foreach(_(state))
    state.processEvent(firstEvent)
    state
  }
  
  def createNewInstance(firstEvent: Meta#Event)( setup: (MyType) => Any): MyType = createNewInstance(firstEvent, Some(setup))

  
  
  /**
    *  Process an event for an instance
    */
  protected def processEvent(who: MyType, what: Meta#Event) {
    val transitions = stateInfo(who.state) // get the transitions
    val which = first(transitions.toList) {t => if (t.on.isDefinedAt(what) && t.testGuard(who, who.state, t.to, what)) Some(t) else None}
    if (!which.isDefined) what.unmatchedEventHanlder(who, stateList(who.state))
    which.foreach {
      t =>
      val to = t.to
      val old = who.state
      stateList.get(old).foreach(_.performExit(who, old, to, what))
      t.performAction(who, old, to, what)
      who.timedEventAt := System.currentTimeMillis
      who.nextTransitionAt := -1L
      stateList.get(to).foreach(_.performSetup(who))
      who.transition(old, to, what)
      stateList.get(to).foreach(_.performEntry(who, old, to, what))
    }
  }
                       
  class State(val name: StV,val trans: Seq[ATransition]) {
    def entry(act: (MyType, StV, StV, Meta#Event) => Any): State = {_entry = act :: _entry; this}
    def exit(act: (MyType, StV, StV, Meta#Event) => Any): State = {_exit = act :: _exit; this}
    private var _entry: List[(MyType, StV, StV, Meta#Event) => Any] = Nil
    private var _exit: List[(MyType, StV, StV, Meta#Event) => Any] = Nil
    
    def performSetup(who: MyType) = trans.foreach(_.performSetup(who, name))
    
    def performEntry(who: MyType, from: StV, to: StV, why: Meta#Event) {_entry.foreach(e => e(who, from, to, why))}
    def performExit(who: MyType, from: StV, to: StV, why: Meta#Event) {_exit.foreach(e => e(who, from, to, why))}
  }
  
  object State {
    def apply(name: StV, trans: ATransition*) = new State(name, trans)
  }
  
  abstract class ATransition(val to: StV,val on: PartialFunction[Meta#Event, Any]) {
    def testGuard(who: MyType, from: StV, to: StV, what: Meta#Event): boolean =
      _guard.isEmpty || _guard.exists(_(who, from, to, what))
      
    def performAction(who: MyType, from: StV, to: StV, what: Meta#Event) {
      _action.foreach(_(who, from, to, what))
    }
    
    def performSetup(who: MyType, to: StV): unit = _setup.foreach(_(who, to))
      
    private var _setup: List[(MyType, StV) => Any] = Nil
    private var _action: List[(MyType, StV, StV, Meta#Event) => Any] = Nil  
    private var _guard: List[(MyType, StV, StV, Meta#Event) => boolean] = Nil
    def action(act: (MyType, StV, StV, Meta#Event) => Any): this.type = {_action = act :: _action; this}
    def guard(gurd: (MyType, StV, StV, Meta#Event) => boolean): this.type = {_guard = gurd :: _guard; this}
    def setup(setp: (MyType, StV) => Any): this.type = {_setup = setp :: _setup; this}
  }
  
  // case class TimeTransition(to: StV, time: TimeSpan) extends Transition
  case class After(when: TimeSpan, override val to: StV) extends ATransition(to, {case TimerEvent(len) if (when.len <= len.len) => true}) {
    setup ((what, state) => what.setupTime(when))
  }
          
  case class TimerEvent(len: TimeSpan) extends Event {
    /**
       * An unhandled event has occurred.  By default, throw an exception.  However,
       * you can override this method (and not call "super") to log issues or do
       * something else
       */
     override def unmatchedEventHanlder(who: MyType, state: State) {
       who.nextTransitionAt := -1L
       state.trans.foreach {
         to =>
         to match {
           case After(when,_) => who.setupTime(when)
           case _ =>
         }
       }
       if (who.nextTransitionAt.get == -1) super.unmatchedEventHanlder(who, state)
       else who.save
     }    
  }
  
  /// case class FirstTransition extends Event
                                                  
  case class To(override val to: StV,override val on: PartialFunction[Meta#Event, Any]) extends ATransition(to, on)
  
  object Event {
    def unmatchedHandler: Option[(MyType,State, Event) => Any] = None
    def unmatchedEventHanlder(who: MyType, state: State, event: Event) {
      val f = unmatchedHandler getOrElse ((who: MyType,state: State,what: Event) => throw new UnmatchedEventException("Event "+what+" was not matched at state "+who.state, who, what))
      f(who, state, event)
    }
  }
  
  abstract class Event {
    /**
       * An unhandled event has occurred.  By default, throw an exception.  However,
       * you can override this method (and not call "super") to log issues or do
       * something else
       */
     def unmatchedEventHanlder(who: MyType, state: State) {
       Event.unmatchedEventHanlder(who, state, this) // throw new UnmatchedEventException("Event "+this+" was not matched at state "+who.state, who, this)
     }

  }
  
  class DuplicateStateException(msg: String) extends Exception(msg) 
  
  class UnmatchedEventException(msg: String,val who: MyType,
                                    val what: Event)  extends Exception(msg)
  
  /**
    * How long to wait to start looking for timed events.  Override this method to
    * specify a time
    */
  def timedEventInitialWait = 120000L
  
  /**
    * After the initial test, how long do we wait 
    */
  def timedEventPeriodicWait = 10000L
  
  private class TimedEventManager(val metaOwner: Meta) extends Actor {
    def act = {
      ActorPing.schedule(this, Ping, timedEventInitialWait) // give the system 2 minutes to "warm up" then start pinging  
      loop
    }
    
    def loop {
      react {
        case Ping() => 
        val now = System.currentTimeMillis
        try {
        val name = metaOwner.nextTransitionAt.dbColumnName
        metaOwner.findAll(BySql(name+" > 0 AND "+name+" <= ?", now)).foreach {
          stateItem =>
          val event = TimerEvent(now - stateItem.timedEventAt.get)
          timedEventHandler ! (stateItem, event)
        }
        } catch {
        case e: Exception => e.printStackTrace // FIXME, log
        case e: MatchError => e.printStackTrace // FIXME, log
        }
        ActorPing.schedule(this, Ping, timedEventPeriodicWait) 
        loop
        
        case _ => loop
      }
    }
    
    case class Ping
  }
    
    private class TimedEventHandler(val metaOwner: Meta) extends Actor {
      def act = loop
      
      def loop {
        react {
          case (item: MyType, event: Event) => 
          try {
          item.processEvent(event)
          } catch {
          case e: Exception  => e.printStackTrace // FIXME, log
          case e: MatchError => e.printStackTrace // FIXME, log
          }
          loop
          
          case _ => loop
        }
      }
      
      case class Ping
    }    
  
  val timedEventManager: Actor = {
    val ret = new TimedEventManager(getSingleton)
    ret.start
    ret
  }
  
  val timedEventHandler: Actor = {
    val ret = new TimedEventHandler(getSingleton)
    ret.start
    ret
  }
}
